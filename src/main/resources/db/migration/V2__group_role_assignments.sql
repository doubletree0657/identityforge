CREATE TABLE group_roles (
    group_id UUID NOT NULL,
    role_id UUID NOT NULL,
    PRIMARY KEY (group_id, role_id),
    CONSTRAINT fk_group_roles_group FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);
