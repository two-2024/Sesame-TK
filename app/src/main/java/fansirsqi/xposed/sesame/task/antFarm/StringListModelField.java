package fansirsqi.xposed.sesame.model.modelFieldExt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import fansirsqi.xposed.sesame.model.ModelField;

/**
 * 字符串列表字段（例如配置多个时间点）
 */
public class StringListModelField extends ModelField<List<String>> {

    public StringListModelField(String key, String desc, List<String> defaultValue) {
        super(key, desc, defaultValue);
    }

    public StringListModelField(String key, String desc, String... defaultValue) {
        super(key, desc, Arrays.asList(defaultValue));
    }

    @Override
    public List<String> fromString(String value) {
        if (value == null || value.trim().isEmpty()) return new ArrayList<>();
        return Arrays.asList(value.split(","));
    }

    @Override
    public String toString(List<String> value) {
        if (value == null || value.isEmpty()) return "";
        return String.join(",", value);
    }
}
