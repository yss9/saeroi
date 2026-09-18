package com.ssafy.upcycle.inquiry.repository;

import com.ssafy.upcycle.inquiry.entity.ConsumerInquiry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumerInquiryRepository extends JpaRepository<ConsumerInquiry, Long> {

    /** 최신 문의가 맨 위. 문의내역 화면은 방금 등록한 것을 바로 보고 싶어한다. */
    List<ConsumerInquiry> findAllByUserIdOrderByIdDesc(Long userId);
}
