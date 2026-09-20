DECLARE @constraint sysname = (
    SELECT cc.name
    FROM sys.check_constraints cc
    WHERE cc.parent_object_id = OBJECT_ID('dbo.transactions')
      AND cc.definition LIKE '%\[type\]%' ESCAPE '\'
);

IF @constraint IS NOT NULL
    EXEC('ALTER TABLE dbo.transactions DROP CONSTRAINT ' + @constraint);

UPDATE dbo.transactions
SET type = CASE WHEN amount < 0 THEN 'EXPENSE' ELSE 'INCOME' END
WHERE type IN ('DEBIT', 'CREDIT');

IF NOT EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID('dbo.transactions') AND name = 'CK_transactions_type'
)
    ALTER TABLE dbo.transactions
        ADD CONSTRAINT CK_transactions_type CHECK (type IN ('INCOME', 'EXPENSE'));
