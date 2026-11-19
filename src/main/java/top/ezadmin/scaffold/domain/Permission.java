package top.ezadmin.scaffold.domain;

public enum Permission {
    SYSTEM_ADMIN("管理用户、角色和权限"),
    ASSET_READ("查看资产"),
    ASSET_WRITE("新增、编辑和删除资产");

    private final String label;

    Permission(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
