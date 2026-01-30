const express = require('express');
const router = express.Router();
const pool = require('./db');
const multer = require('multer');

const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        cb(null, 'public/uploads/');
    },
    filename: (req, file, cb) => {
        cb(null, Date.now() + '-' + file.originalname);
    }
});

const upload = multer({ storage: storage });

router.post('/update_daily_check', upload.array('new_images', 5), async (req, res) => {
    const client = await pool.connect();
    try {
        await client.query('BEGIN');

        const { check_id, location, hazard, countermeasure, check_date, kept_image_urls } = req.body;

        if (!check_id) {
            throw new Error("check_id is required");
        }

        // 1. daily_safety_check 테이블 업데이트
        let updateQuery = `
            UPDATE daily_safety_check 
            SET location = $1, hazard = $2, countermeasure = $3, created_at = now()
        `;
        const params = [location, hazard, countermeasure];
        let paramIdx = 4;

        if (check_date) {
            updateQuery += `, check_date = $${paramIdx}`;
            params.push(check_date);
            paramIdx++;
        }

        updateQuery += ` WHERE check_id = $${paramIdx}`;
        params.push(check_id);

        await client.query(updateQuery, params);

        // 2. 이미지 처리
        // 유지할 이미지 URL 목록 (없으면 빈 배열)
        let keptUrls = [];
        if (kept_image_urls) {
            keptUrls = Array.isArray(kept_image_urls) ? kept_image_urls : [kept_image_urls];
        }

        // 유지 목록에 없는 이미지는 DB에서 삭제
        if (keptUrls.length > 0) {
            const placeholders = keptUrls.map((_, i) => `$${i + 2}`).join(',');
            await client.query(`DELETE FROM check_images WHERE check_id = $1 AND image_url NOT IN (${placeholders})`, [check_id, ...keptUrls]);
        } else {
            await client.query(`DELETE FROM check_images WHERE check_id = $1`, [check_id]);
        }

        // 새로운 이미지 삽입
        if (req.files && req.files.length > 0) {
            const insertImageQuery = `INSERT INTO check_images (check_id, image_url) VALUES ($1, $2)`;
            for (const file of req.files) {
                const imageUrl = `http://${req.headers.host}/uploads/${file.filename}`;
                await client.query(insertImageQuery, [check_id, imageUrl]);
                keptUrls.push(imageUrl); // 결과 반환용 리스트에 추가
            }
        }

        await client.query('COMMIT');

        // 최신 이미지 목록 조회하여 반환
        const imagesResult = await client.query('SELECT image_url FROM check_images WHERE check_id = $1', [check_id]);
        const allImages = imagesResult.rows.map(row => row.image_url);

        res.status(200).json({ 
            message: "점검 리스트가 수정되었습니다.",
            check_id: parseInt(check_id),
            image_urls: allImages
        });

    } catch (err) {
        await client.query('ROLLBACK');
        console.error("Error updating daily check:", err);
        res.status(500).json({ message: "서버 오류 발생" });
    } finally {
        client.release();
    }
});

module.exports = router;