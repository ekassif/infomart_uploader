package com.bmo.infomartfileloader.util;

import lombok.*;

/**
 * Represents method execution results but includes possibility of passing back errors
 * @param <T>
 */

@Data @Builder
@AllArgsConstructor @NoArgsConstructor
public class Results<T> {
    T value;
    int returnCode;
    Exception error;
    String errorMessage;

    public boolean isOK(){
        return returnCode == 0;
    }
}
