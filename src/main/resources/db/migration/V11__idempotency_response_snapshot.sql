ALTER TABLE idempotency_keys
    ADD COLUMN result_response_json TEXT NULL AFTER result_transaction_id;
