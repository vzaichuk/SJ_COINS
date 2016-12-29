package com.softjourn.coin.server.repository;

import com.softjourn.coin.server.entity.Contract;
import com.softjourn.coin.server.entity.Type;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    List<Contract> findContractByTypeType(String type);

}
