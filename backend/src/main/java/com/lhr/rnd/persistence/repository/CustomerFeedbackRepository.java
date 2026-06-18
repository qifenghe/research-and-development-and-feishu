package com.lhr.rnd.persistence.repository;

import com.lhr.rnd.persistence.entity.CustomerFeedbackEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerFeedbackRepository extends JpaRepository<CustomerFeedbackEntity, String> {
}
