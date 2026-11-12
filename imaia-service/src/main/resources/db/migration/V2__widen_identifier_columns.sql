-- Daemon message IDs are long base64-encoded strings (200+ characters); the initial
-- varchar(128) columns reject them. Widen every identifier column to text.
ALTER TABLE processed_message ALTER COLUMN message_id TYPE TEXT;
ALTER TABLE processed_message ALTER COLUMN thread_id TYPE TEXT;
ALTER TABLE conversation_turn ALTER COLUMN account_id TYPE TEXT;
ALTER TABLE conversation_turn ALTER COLUMN thread_id TYPE TEXT;
