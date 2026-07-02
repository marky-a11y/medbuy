package com.autoresolve.mediabuying.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageDTO<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private Map<String, String> sort;
}
