package com.knoc.reviewFeedback.repository;

import com.knoc.senior.entity.SeniorProfile;
import com.knoc.reviewFeedback.entity.ReviewFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface ReviewFeedbackRepository extends JpaRepository<ReviewFeedback,Long> {
    boolean existsByOrderId(Long orderId);

    java.util.Optional<ReviewFeedback> findByOrderId(Long orderId);

    List<ReviewFeedback> findTop3BySeniorProfile_IdOrderByCreatedAtDesc(Long seniorProfileId);

    List<ReviewFeedback> findBySeniorProfile_Id(Long seniorProfileId);

    List<ReviewFeedback> findBySeniorProfile_IdOrderByCreatedAtDesc(Long seniorProfileId);

    org.springframework.data.domain.Page<ReviewFeedback> findBySeniorProfile_IdOrderByCreatedAtDesc(Long seniorProfileId, org.springframework.data.domain.Pageable pageable);

    @Query("SELECT r.order.id FROM ReviewFeedback r WHERE r.order.id IN :orderIds")
    Set<Long> findReviewedOrderIds(@Param("orderIds") List<Long> orderIds);

    @Query("SELECT r FROM ReviewFeedback r JOIN FETCH r.junior WHERE r.seniorProfile.id = :id ORDER BY r.createdAt DESC")
    List<ReviewFeedback> findTop3WithJuniorBySeniorProfileId(@Param("id") Long seniorProfileId, Pageable pageable);

    // 페이지네이션 대체
    @Query(
            value = "SELECT r FROM ReviewFeedback r JOIN FETCH r.junior WHERE r.seniorProfile.id = :id ORDER BY r.createdAt DESC",
            countQuery = "SELECT COUNT(r) FROM ReviewFeedback r WHERE r.seniorProfile.id = :id"
    )
    Page<ReviewFeedback> findWithJuniorBySeniorProfileId(@Param("id") Long seniorProfileId, Pageable pageable);

    @Query("SELECT r FROM ReviewFeedback r JOIN FETCH r.junior JOIN FETCH r.seniorProfile sp JOIN FETCH sp.member JOIN FETCH r.order ORDER BY r.createdAt DESC ")
    List<ReviewFeedback> findAllWithRelationsOrderByCreatedAtDesc();

    @Query("SELECT r FROM ReviewFeedback r JOIN FETCH r.junior JOIN FETCH r.seniorProfile sp JOIN FETCH sp.member JOIN FETCH r.order WHERE r.junior.id = :juniorId ORDER BY r.createdAt DESC")
    List<ReviewFeedback> findByJuniorIdWithRelations(@Param("juniorId") Long juniorId);

    @Query("SELECT sp FROM SeniorProfile sp JOIN FETCH sp.member " + "WHERE sp.id IN (" + "SELECT r.seniorProfile.id FROM ReviewFeedback r " + "WHERE r.createdAt >= :startOfMonth " + "GROUP BY r.seniorProfile.id " + "ORDER BY COUNT(r) DESC" + ")")
    List<SeniorProfile> findTop3ActiveSeniorsThisMonthWithMember(@Param("startOfMonth") LocalDateTime startOfMonth, Pageable pageable);
}
