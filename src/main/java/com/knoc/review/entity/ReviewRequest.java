package com.knoc.review.entity;

import com.knoc.order.entity.Order;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "review_request")
public class ReviewRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(name = "github_pr_url", nullable = false, length = 512)
    private String githubPrUrl;

    @Column(name = "project_context", nullable = false, columnDefinition = "TEXT")
    private String projectContext;

    @Column(name = "concern_point", nullable = false, columnDefinition = "TEXT")
    private String concernPoint;

    @Column(name = "ai_pr_summary", columnDefinition = "TEXT")
    private String aiPrSummary;

    @Column(name = "changed_files", nullable = false)
    private int changedFiles;

    @Column(nullable = false)
    private int additions;

    @Column(nullable = false)
    private int deletions;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ReviewRequest(Order order, String githubPrUrl, String projectContext,
                         String concernPoint, int changedFiles, int additions, int deletions) {
        this.order = order;
        this.githubPrUrl = githubPrUrl;
        this.projectContext = projectContext;
        this.concernPoint = concernPoint;
        this.changedFiles = changedFiles;
        this.additions = additions;
        this.deletions = deletions;
    }
}