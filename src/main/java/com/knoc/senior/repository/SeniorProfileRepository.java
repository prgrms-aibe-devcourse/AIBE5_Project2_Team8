package com.knoc.senior.repository;

import com.knoc.senior.entity.SeniorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeniorProfileRepository extends JpaRepository<SeniorProfile,Long> {
    Optional<SeniorProfile> findByMemberId(Long memberId);
}
