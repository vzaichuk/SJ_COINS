package com.softjourn.coin.server.service;


import com.softjourn.coin.server.entity.Account;
import com.softjourn.coin.server.entity.TransactionStatus;
import com.softjourn.coin.server.exceptions.AccountNotFoundException;
import com.softjourn.coin.server.exceptions.NotEnoughAmountInAccountException;
import com.softjourn.coin.server.repository.TransactionRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.security.Principal;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.AdditionalMatchers.not;
import static org.mockito.AdditionalMatchers.or;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class CoinServiceMoveTest {

    Account account1;
    Account account2;

    @Mock
    Principal principal;

    @Mock
    AccountsService accountsService;

    @Mock
    TransactionRepository transactionRepository;

    CoinService coinService;

    @Before
    public void setUp() throws Exception {

        account1 = new Account("account1", new BigDecimal(100));
        account2 = new Account("account2", new BigDecimal(200));

        when(principal.getName()).thenReturn("account1");

        coinService = new CoinService(accountsService);

        when(accountsService.getAccount("account1")).thenReturn(account1);
        when(accountsService.getAccount("account2")).thenReturn(account2);
        when(accountsService.getAccount(not(or(eq("account1"), eq("account2"))))).thenThrow(new AccountNotFoundException(""));
    }

    @Test
    public void testMove() throws Exception {
        coinService.move(principal.getName(), "account2", new BigDecimal(50), "");

        assertEquals(new BigDecimal(50), account1.getAmount());
        assertEquals(new BigDecimal(250), account2.getAmount());

        verify(accountsService, times(2)).update(any(Account.class));
    }

    @Test(expected = NotEnoughAmountInAccountException.class)
    public void testMoveTooMuch() throws Exception {
        assertEquals(TransactionStatus.FAILED, coinService.move(principal.getName(), "account2", new BigDecimal(500), "").getStatus());

        assertEquals(new BigDecimal(100), account1.getAmount());
        assertEquals(new BigDecimal(200), account2.getAmount());

        verify(accountsService, times(0)).update(any(Account.class));
    }

    @Test(expected = AccountNotFoundException.class)
    public void testMoveToWrongAccount() throws Exception {
        assertEquals(TransactionStatus.FAILED, coinService.move(principal.getName(), "account3", new BigDecimal(50), "").getStatus());

        assertEquals(new BigDecimal(100), account1.getAmount());
        assertEquals(new BigDecimal(200), account2.getAmount());
    }

    @Test
    public void testMoveExceptionInTheMiddle() throws Exception {
        when(accountsService.getAccount("account2")).thenThrow(new AccountNotFoundException(""));

        try {
            assertEquals(TransactionStatus.FAILED, coinService.move(principal.getName(), "account2", new BigDecimal(50), "").getStatus());
        } catch (AccountNotFoundException ignored) {

        }

        assertEquals(new BigDecimal(100), account1.getAmount());
        assertEquals(new BigDecimal(200), account2.getAmount());
    }

}