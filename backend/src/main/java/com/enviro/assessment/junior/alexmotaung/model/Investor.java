package com.enviro.assessment.junior.alexmotaung.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "investors")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Investor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @NotBlank @Column(nullable = false)
    private String firstName;
    @NotBlank @Column(nullable = false)
    private String lastName;
    @Email @NotBlank @Column(nullable = false, unique = true)
    private String email;
    @Min(0) @Column(nullable = false)
    private Integer age;

    public Investor(String firstName, String lastName, String email, Integer age) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.age = age;
    }
}
