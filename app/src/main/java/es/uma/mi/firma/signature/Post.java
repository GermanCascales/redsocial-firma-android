package es.uma.mi.firma.signature;

import com.google.gson.annotations.SerializedName;

public class Post {
    @SerializedName("id")
    Integer id;

    @SerializedName("title")
    String title;

    @SerializedName("description")
    String description;

    public Post(Integer id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public Integer getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }
}
