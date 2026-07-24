package io.pragmatic.ddd.repository;



import java.util.ArrayList;
import java.util.List;


public class PageInfoDAO<T> {

    private Long totalRows;
    private List<T> data = new ArrayList<>();

    public Long getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(Long totalRows) {
        this.totalRows = totalRows;
    }

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }
}
