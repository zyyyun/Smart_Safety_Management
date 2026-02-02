const express = require('express');
const router = express.Router();
const pool = require('./db');

router.post('/delete_daily_check', async (req, res) => {
    const { check_id } = req.body;

    if (!check_id) {
        return res.status(400).json({ message: "check_id is required" });
    }

    try {
        const query = 'DELETE FROM daily_safety_check WHERE check_id = $1';
        const result = await pool.query(query, [check_id]);

        if (result.rowCount === 0) {
            return res.status(404).json({ message: "Check not found" });
        }

        res.status(200).json({ message: "Deleted successfully" });
    } catch (err) {
        console.error('Error deleting daily check:', err);
        res.status(500).json({ message: "Server error" });
    }
});

module.exports = router;