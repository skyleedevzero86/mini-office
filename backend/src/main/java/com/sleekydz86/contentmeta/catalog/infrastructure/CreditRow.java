package com.sleekydz86.contentmeta.catalog.infrastructure;

/** 출연·제작진 한 건 */
public class CreditRow {

    private long personId;
    private String personName = "";
    private String roleType = "";
    private String characterName = "";
    private int creditOrder;
    private boolean nameOnly = true;

    public long getPersonId() {
        return personId;
    }

    public void setPersonId(long personId) {
        this.personId = personId;
    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public String getRoleType() {
        return roleType;
    }

    public void setRoleType(String roleType) {
        this.roleType = roleType;
    }

    public String getCharacterName() {
        return characterName;
    }

    public void setCharacterName(String characterName) {
        this.characterName = characterName;
    }

    public int getCreditOrder() {
        return creditOrder;
    }

    public void setCreditOrder(int creditOrder) {
        this.creditOrder = creditOrder;
    }

    public boolean isNameOnly() {
        return nameOnly;
    }

    public void setNameOnly(boolean nameOnly) {
        this.nameOnly = nameOnly;
    }

}
