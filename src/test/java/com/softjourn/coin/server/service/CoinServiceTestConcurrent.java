package com.softjourn.coin.server.service;


import com.softjourn.coin.server.entity.Account;
import com.softjourn.coin.server.entity.FabricAccount;
import com.softjourn.coin.server.entity.ErisAccountType;
import com.softjourn.coin.server.repository.FabricAccountRepository;
import com.softjourn.coin.server.repository.TransactionRepository;
import com.softjourn.eris.contract.Contract;
import com.softjourn.eris.contract.response.Response;
import com.softjourn.eris.contract.response.TxParams;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.Principal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CoinServiceTestConcurrent {

    Account account;
    Account sellerAccount;

    @Mock
    Principal principal;

    @Mock
    ErisContractService contractService;

    @Mock
    TransactionRepository transactionRepository;

    @Mock
    AccountsService accountsService;

    @Mock
    FabricAccountRepository fabricAccountRepository;

    @Mock
    Contract contract;

    CoinService coinService;

    private AtomicInteger amount = new AtomicInteger(200000);

    @Before
    public void setUp() throws Exception {

        account = new Account("user", new BigDecimal(200000));
        sellerAccount = new Account("seller", new BigDecimal(0));

        FabricAccount fabricAccount1 = new FabricAccount();
        fabricAccount1.setAddress("address1");
        fabricAccount1.setType(ErisAccountType.ROOT);
        account.setFabricAccount(fabricAccount1);

        FabricAccount sellerFabricAccount = new FabricAccount();
        sellerFabricAccount.setAddress("address");
        sellerAccount.setFabricAccount(sellerFabricAccount);

        when(principal.getName()).thenReturn("user");

        when(accountsService.getAccount("user")).thenReturn(account);
        when(accountsService.getAccount("seller")).thenReturn(sellerAccount);
        TransactionMapper mapper = mock(TransactionMapper.class);

        coinService = new CoinService(accountsService, contractService, fabricAccountRepository, transactionRepository, mapper);

        when(contractService.getTokenContractForAccount(any())).thenReturn(contract);

        Response getResp = new Response("",
                Collections.singletonList(BigInteger.valueOf(100000L)),
                null,
                null);

        Response sendResp = new Response("",
                null,
                null,
                new TxParams("address", "txId"));

        when(contract.call(eq("balanceOf"), anyVararg()))
                .thenReturn(getResp);

        when(contract.call(eq("transfer"), eq("address"), org.mockito.Matchers.anyVararg()))
                .then((InvocationOnMock invocation) -> {
                        amount.addAndGet(-200);
                        return sendResp;
                    }
                );

        when(contract.call(eq("transfer"), eq("address1"), org.mockito.Matchers.anyVararg()))
                .then(inv -> {
                    amount.addAndGet(100);
                    return sendResp;
                });
    }

    /*
     * Test adding and getting money in concurrent environment
     */
    @Test
    public void test() throws Exception {
        ExecutorService executorService = new ForkJoinPool(10);
        Collection<Callable<Object>> tasks = new HashSet<>();

        for (int i = 0; i < 1000; i++) {
            tasks.add(() -> coinService.fillAccount("user", new BigDecimal(100), ""));
            tasks.add(() -> coinService.buy("seller", principal.getName(), new BigDecimal(200), ""));
        }

        executorService.invokeAll(tasks);
        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.SECONDS);
    }

    @After
    public void tearDown() throws Exception {
        assertEquals(new AtomicInteger(100000).get(), amount.get());
    }
}