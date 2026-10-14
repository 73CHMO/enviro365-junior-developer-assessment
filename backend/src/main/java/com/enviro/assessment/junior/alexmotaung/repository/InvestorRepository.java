package com.enviro.assessment.junior.alexmotaung.repository;

import com.enviro.assessment.junior.alexmotaung.model.Investor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvestorRepository extends JpaRepository<Investor, Long> {
}
