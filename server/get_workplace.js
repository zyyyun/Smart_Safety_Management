const express = require('express');
const router = express.Router();
const pool = require('./db');

router.get('/get_workplace', async (req, res) => {
    const { user_id } = req.query;

    if (!user_id) {
        return res.status(400).json({ message: "user_id가 필요합니다." });
    }

    try {
        const result = await pool.query('SELECT place_name FROM workplace WHERE admin_id = $1', [user_id]);
        const workplaces = result.rows.map(row => ({ name: row.place_name }));
        res.status(200).json({ workplaces: workplaces });
    } catch (err) {
        console.error(err);
        res.status(500).json({ message: "서버 오류 발생" });
    }
});

module.exports = router;