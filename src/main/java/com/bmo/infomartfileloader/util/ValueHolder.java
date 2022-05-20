package com.bmo.infomartfileloader.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class ValueHolder<T> {
    T value;

    public static <T> ValueHolder<T> empty(){
        return new ValueHolder();
    }

    public static <T> ValueHolder<T> of(T t){
        return new ValueHolder(t);
    }

    public boolean isNull() {
        return value == null;
    }
}
