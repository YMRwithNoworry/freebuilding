package com.freebuilding.city;

public final class CategorySetting {
    public int weight = 10;

    public CategorySetting() {
    }

    public CategorySetting(int weight) {
        this.weight = weight;
    }

    public CategorySetting copy() {
        return new CategorySetting(weight);
    }
}
