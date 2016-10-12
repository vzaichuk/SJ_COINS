DROP TABLE eris IF EXISTS CASCADE;
DROP TABLE accounts IF EXISTS CASCADE;
DROP TABLE transactions IF EXISTS;

CREATE TABLE accounts
(
    ldap_id VARCHAR(255) NOT NULL PRIMARY KEY,
    amount DECIMAL(10),
    fullName VARCHAR(255),
    type VARCHAR(32) DEFAULT 'REGULAR',
    image VARCHAR(255)
);

CREATE TABLE transactions
(
    id INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    account_id VARCHAR(255),
    destination_id VARCHAR(255),
    amount DECIMAL(10),
    comment LONGVARCHAR,
    created TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL,
    CONSTRAINT account_fk FOREIGN KEY (account_id) REFERENCES accounts (ldap_id),
    CONSTRAINT destination_fk FOREIGN KEY (destination_id) REFERENCES accounts (ldap_id)
);
CREATE INDEX account_fk ON transactions (account_id);
CREATE INDEX destination_fk ON transactions (destination_id);
CREATE TABLE eris
(
    address VARCHAR(255) PRIMARY KEY NOT NULL,
    privKey VARCHAR(255),
    pubKey VARCHAR(255),
    type INT,
    account_ldap_id VARCHAR(255),
    CONSTRAINT FK55s0o6jfa48iqty8bc1nxxrf2 FOREIGN KEY (account_ldap_id) REFERENCES accounts (ldap_id)
);
CREATE INDEX FK55s0o6jfa48iqty8bc1nxxrf2 ON eris (account_ldap_id);
CREATE UNIQUE INDEX UKrpsbty0f34qu89n6juppia3ge ON eris (type, account_ldap_id);