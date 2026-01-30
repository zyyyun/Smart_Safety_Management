const express = require('express');
const router = express.Router();
const pool = require('./db');
const multer = require('multer');
const path = require('path');

const storage = multer.diskStorage({
    destination: (req, file, cb) => {
        cb(null, 'public/uploads/');
    },
    filename: (req, file, cb) => {
        cb(null, Date.now() + '-' + file.originalname);
    }
});

const upload = multer({ storage: storage });

router.post('/create_daily_check', upload.array('images', 5), async (req, res) => {
    const client = await pool.connect();
    try {
        await client.query('BEGIN');

        const { writer_id, location, hazard, countermeasure, check_date } = req.body;
        
        // 1. daily_safety_check Insert
        const checkQuery = `
            INSERT INTO daily_safety_check (writer_id, location, hazard, countermeasure, check_date, status)
            VALUES ($1, $2, $3, $4, $5, '미점검')
            RETURNING check_id
        `;
        const checkResult = await client.query(checkQuery, [writer_id, location, hazard, countermeasure, check_date]);
        const checkId = checkResult.rows[0].check_id;

        // 2. check_images Insert
        const imageUrls = [];
        if (req.files && req.files.length > 0) {
            const imageQuery = `
                INSERT INTO check_images (check_id, image_url)
                VALUES ($1, $2)
            `;
            for (const file of req.files) {
                const imageUrl = `http://${req.headers.host}/uploads/${file.filename}`;
                await client.query(imageQuery, [checkId, imageUrl]);
                imageUrls.push(imageUrl);
            }
        }

        await client.query('COMMIT');
        
        res.status(200).json({ 
            message: "점검 리스트가 생성되었습니다.",
            check_id: checkId,
            image_urls: imageUrls
        });

    } catch (err) {
        await client.query('ROLLBACK');
        console.error("Error creating daily check:", err);
        res.status(500).json({ message: "서버 오류 발생" });
    } finally {
        client.release();
    }
});

module.exports = router;