package com.knoc.senior.repository;

import com.knoc.senior.entity.SeniorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeniorProfileRepository extends JpaRepository<SeniorProfile,Long> {
}
