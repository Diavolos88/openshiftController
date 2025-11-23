-- SQL скрипт для удаления столбца is_mock из таблицы openshift_connections
-- Выполните этот скрипт в PostgreSQL для удаления mock-функциональности

-- Подключитесь к базе данных openshift_controller
\c openshift_controller;

-- Удаление столбца is_mock, если он существует
DO $$ 
BEGIN 
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = 'openshift_connections' 
        AND column_name = 'is_mock'
    ) THEN
        ALTER TABLE openshift_connections 
        DROP COLUMN is_mock;
        
        RAISE NOTICE 'Столбец is_mock успешно удален из таблицы openshift_connections';
    ELSE
        RAISE NOTICE 'Столбец is_mock не существует в таблице openshift_connections';
    END IF;
END $$;

