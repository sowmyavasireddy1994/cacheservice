package com.example.cacheservice.entity;

import javax.persistence.*;

import lombok.*;

@Entity
@Table(name = "MY_ENTITY")
@Getter
@Setter
@Data
@NoArgsConstructor
public class MyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;


    public MyEntity(Long id, String name) {
        this.id = id;
        this.name = name;
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

    // Unique id can be the database id here, or any other unique field.
    // getId() will serve as getId() from requirements.
}
