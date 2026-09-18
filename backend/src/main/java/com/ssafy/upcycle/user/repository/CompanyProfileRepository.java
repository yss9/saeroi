package com.ssafy.upcycle.user.repository;

import com.ssafy.upcycle.user.entity.CompanyProfile;
import com.ssafy.upcycle.user.entity.CompanyRoleApprovalStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, Long> {
    List<CompanyProfile> findAllByLatitudeIsNullOrLongitudeIsNull();

    List<CompanyProfile> findAllByRoleApprovalStatus(CompanyRoleApprovalStatus roleApprovalStatus);
}
