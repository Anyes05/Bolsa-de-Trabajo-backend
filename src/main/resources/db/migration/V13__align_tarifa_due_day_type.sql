ALTER TABLE tarifas
    ALTER COLUMN dia_vencimiento TYPE INTEGER USING dia_vencimiento::INTEGER;