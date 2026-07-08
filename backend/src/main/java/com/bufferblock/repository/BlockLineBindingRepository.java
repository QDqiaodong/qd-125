package com.bufferblock.repository;

import com.bufferblock.entity.BlockLineBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockLineBindingRepository extends JpaRepository<BlockLineBinding, Long> {

    Optional<BlockLineBinding> findByBlockIdAndIsCurrent(Long blockId, Integer isCurrent);

    List<BlockLineBinding> findByBlockIdOrderByBindTimeDesc(Long blockId);

    List<BlockLineBinding> findByLineIdAndIsCurrent(Long lineId, Integer isCurrent);

    List<BlockLineBinding> findByLineIdInAndIsCurrent(List<Long> lineIds, Integer isCurrent);
}
