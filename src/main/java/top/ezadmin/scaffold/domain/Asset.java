package top.ezadmin.scaffold.domain;

public class Asset {
    private Long id;
    private String name;
    private String notes;

    public Asset() {
    }

    public Asset(Long id, String name, String notes) {
        this.id = id;
        this.name = name;
        this.notes = notes;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
