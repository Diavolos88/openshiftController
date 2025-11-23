-- SQL скрипт для добавления столбца is_mock в таблицу openshift_connections
-- Выполните этот скрипт в PostgreSQL для обновления базы данных

-- Подключитесь к базе данных openshift_controller
\c openshift_controller;

-- Добавление столбца is_mock, если его еще нет
DO $$ 
BEGIN 
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = 'openshift_connections' 
        AND column_name = 'is_mock'
    ) THEN
        ALTER TABLE openshift_connections 
        ADD COLUMN is_mock BOOLEAN NOT NULL DEFAULT false;
        
        RAISE NOTICE 'Столбец is_mock успешно добавлен в таблицу openshift_connections';
    ELSE
        RAISE NOTICE 'Столбец is_mock уже существует в таблице openshift_connections';
    END IF;
END $$;

