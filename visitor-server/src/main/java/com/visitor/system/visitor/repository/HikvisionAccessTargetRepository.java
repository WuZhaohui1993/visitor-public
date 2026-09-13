package com.visitor.system.visitor.repository;

import com.visitor.system.visitor.domain.HikvisionAccessTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HikvisionAccessTargetRepository extends JpaRepository<HikvisionAccessTarget, Long> {

    List<HikvisionAccessTarget> findAllByOrderByResourceIndexCodeAscChannelNoAsc();
}
