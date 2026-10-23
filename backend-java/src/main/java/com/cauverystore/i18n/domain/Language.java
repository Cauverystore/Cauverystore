package com.cauverystore.i18n.domain;

import jakarta.persistence.*;

/** A language the site is offered in. Add a language = add a row to the languages table. */
@Entity
@Table(name = "languages")
public class Language {
    @Id
    @Column(length = 10)
    private String code;
    @Column(name = "name_english", nullable = false) private String nameEnglish;
    @Column(name = "name_native", nullable = false)  private String nameNative;
    @Column(nullable = false) private String script;
    @Column(name = "text_direction", nullable = false) private String textDirection = "ltr";
    @Column(name = "is_default", nullable = false) private boolean isDefault;
    @Column(name = "is_active", nullable = false)  private boolean isActive = true;
    @Column(name = "sort_order", nullable = false) private int sortOrder = 100;

    protected Language() {}

    public String getCode() { return code; }
    public String getNameEnglish() { return nameEnglish; }
    public String getNameNative() { return nameNative; }
    public String getScript() { return script; }
    public String getTextDirection() { return textDirection; }
    public boolean isDefault() { return isDefault; }
    public boolean isActive() { return isActive; }
    public int getSortOrder() { return sortOrder; }
}
