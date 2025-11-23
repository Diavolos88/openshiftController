-- SQL скрипт для миграции базы данных к новой архитектуре с группами
-- Выполните этот скрипт в PostgreSQL для обновления базы данных

-- Подключитесь к базе данных openshift_controller
\c openshift_controller;

-- Шаг 1: Создать таблицу connection_groups (если не существует)
CREATE TABLE IF NOT EXISTS connection_groups (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Шаг 2: Проверить и переименовать столбец default_namespace в namespace
DO $$ 
BEGIN 
    -- Проверяем, существует ли столбец default_namespace
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = 'openshift_connections' 
        AND column_name = 'default_namespace'
    ) THEN
        -- Проверяем, существует ли уже столбец namespace
        IF NOT EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_schema = 'public' 
            AND table_name = 'openshift_connections' 
            AND column_name = 'namespace'
        ) THEN
            -- Переименовываем столбец
            ALTER TABLE openshift_connections 
            RENAME COLUMN default_namespace TO namespace;
            
            RAISE NOTICE 'Столбец default_namespace переименован в namespace';
        ELSE
            -- Если namespace уже существует, обновляем его значениями из default_namespace
            UPDATE openshift_connections 
            SET namespace = default_namespace 
            WHERE namespace IS NULL OR namespace = '';
            
            -- Удаляем старый столбец
            ALTER TABLE openshift_connections 
            DROP COLUMN IF EXISTS default_namespace;
            
            RAISE NOTICE 'Значения из default_namespace перенесены в namespace, старый столбец удален';
        END IF;
    ELSE
        RAISE NOTICE 'Столбец default_namespace не найден, возможно уже переименован';
    END IF;
END $$;

-- Шаг 3: Убедиться, что столбец namespace существует и установить NOT NULL (если нет NULL значений)
DO $$ 
BEGIN 
    -- Проверяем, существует ли столбец namespace
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = 'openshift_connections' 
        AND column_name = 'namespace'
    ) THEN
        -- Обновляем NULL значения на 'default'
        UPDATE openshift_connections 
        SET namespace = 'default' 
        WHERE namespace IS NULL OR namespace = '';
        
        -- Устанавливаем NOT NULL, если столбец еще не NOT NULL
        IF EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_schema = 'public' 
            AND table_name = 'openshift_connections' 
            AND column_name = 'namespace'
            AND is_nullable = 'YES'
        ) THEN
            ALTER TABLE openshift_connections 
            ALTER COLUMN namespace SET NOT NULL;
            
            RAISE NOTICE 'Столбец namespace установлен как NOT NULL';
        END IF;
    ELSE
        -- Если столбца namespace нет, создаем его
        ALTER TABLE openshift_connections 
        ADD COLUMN namespace VARCHAR(100) NOT NULL DEFAULT 'default';
        
        RAISE NOTICE 'Столбец namespace создан';
    END IF;
END $$;

-- Шаг 4: Добавить столбец group_id в таблицу openshift_connections
DO $$ 
BEGIN 
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = 'openshift_connections' 
        AND column_name = 'group_id'
    ) THEN
        ALTER TABLE openshift_connections 
        ADD COLUMN group_id BIGINT;
        
        -- Добавляем внешний ключ
        ALTER TABLE openshift_connections 
        ADD CONSTRAINT fk_connection_group 
        FOREIGN KEY (group_id) REFERENCES connection_groups(id) 
        ON DELETE SET NULL;
        
        RAISE NOTICE 'Столбец group_id добавлен в таблицу openshift_connections';
    ELSE
        RAISE NOTICE 'Столбец group_id уже существует';
    END IF;
END $$;

-- Шаг 5: Создать индекс для внешнего ключа (для производительности)
CREATE INDEX IF NOT EXISTS idx_openshift_connections_group_id 
ON openshift_connections(group_id);

-- Миграция завершена успешно!

