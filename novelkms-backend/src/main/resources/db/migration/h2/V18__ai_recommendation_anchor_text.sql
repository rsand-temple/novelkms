-- V18: Add anchor_text to AI review recommendations for click-to-scroll.
ALTER TABLE ai_review_recommendation ADD COLUMN anchor_text TEXT;
