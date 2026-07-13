package com.hmdm.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "remote_files")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RemoteFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configuration_id", nullable = false)
    private Configuration configuration;

    @Column(nullable = false, length = 500)
    private String path;

    @Column(length = 1000)
    private String url;

    @Column(length = 500)
    private String description;

    @Column(length = 100)
    private String checksum;

    @Column
    @Builder.Default
    private Boolean remove = false;
}
