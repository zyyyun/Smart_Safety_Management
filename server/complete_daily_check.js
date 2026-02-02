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

router.post('/complete_daily_check', upload.array('images', 5), async (req, res) => {
    const { check_id, worker_id, check_content, check_date } = req.body;

    if (!check_id) {
        return res.status(400).json({ message: "check_id is required" });
    }

    const client = await pool.connect();
    try {
        await client.query('BEGIN');

        const query = `
            UPDATE daily_safety_check
            SET status = '점검완료', worker_id = $1, check_date = $2, check_content = $3
            WHERE check_id = $4
        `;
        const result = await client.query(query, [worker_id, check_date, check_content, check_id]);

        if (result.rowCount === 0) {
            await client.query('ROLLBACK');
            return res.status(404).json({ message: "Check not found" });
        }

        if (req.files && req.files.length > 0) {
            const imageQuery = `INSERT INTO check_images (check_id, image_url) VALUES ($1, $2)`;
            for (const file of req.files) {
                const imageUrl = `http://${req.headers.host}/uploads/${file.filename}`;
                await client.query(imageQuery, [check_id, imageUrl]);
            }
        }

        await client.query('COMMIT');
        res.status(200).json({ message: "Check completed successfully" });
    } catch (err) {
        await client.query('ROLLBACK');
        console.error('Error completing daily check:', err);
        res.status(500).json({ message: "Server error" });
    } finally {
        client.release();
    }
});

module.exports = router;