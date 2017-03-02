package com.softjourn.coin.server.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.softjourn.coin.server.entity.Transaction
import com.softjourn.coin.server.service.GenericFilter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.orm.jpa.AutoConfigureTestDatabase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.data.domain.Sort
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.restdocs.JUnitRestDocumentation
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.common.OAuth2AccessToken
import org.springframework.security.oauth2.provider.OAuth2Authentication
import org.springframework.security.oauth2.provider.OAuth2Request
import org.springframework.security.oauth2.provider.token.DefaultTokenServices
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter
import org.springframework.security.oauth2.provider.token.store.KeyStoreKeyFactory
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

import java.security.KeyPair
import java.util.stream.Collectors

import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import static org.springframework.restdocs.operation.preprocess.Preprocessors.*
import static org.springframework.restdocs.payload.PayloadDocumentation.*
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import static org.springframework.restdocs.request.RequestDocumentation.requestParameters
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@RunWith(SpringJUnit4ClassRunner)
@SpringBootTest(classes = ControllerTestConfig.class)
@AutoConfigureTestDatabase
@WebAppConfiguration
class TransactionControllerTest {

    @Autowired
    private DefaultTokenServices tokenService
    @Value('${authKeyFileName}')
    private String authKeyFileName
    @Value('${authKeyStorePass}')
    private String authKeyStorePass
    @Value('${authKeyMasterPass}')
    private String authKeyMasterPass
    @Value('${authKeyAlias}')
    private String authKeyAlias

    @Rule
    public final JUnitRestDocumentation restDocumentation = new JUnitRestDocumentation('target/generated-snippets')

    @Autowired
    private WebApplicationContext context

    private MockMvc mockMvc

    private GenericFilter<Transaction> filter

    private GenericFilter<Transaction> filterWithoutOrdering

    private GenericFilter<Transaction> filterWithWrongType

    private GenericFilter.PageRequestImpl myTxsPageRequest

    @Before
    synchronized void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .apply(documentationConfiguration(restDocumentation))
                .build()

        GenericFilter.Condition eqCondition = new GenericFilter.Condition("amount", 100, GenericFilter.Comparison.eq)
        GenericFilter.Condition gtCondition = new GenericFilter.Condition("created", "2017-02-10T10:12:45Z", GenericFilter.Comparison.gt)
        GenericFilter.Condition ltCondition = new GenericFilter.Condition("created", "2017-02-14T10:12:45Z", GenericFilter.Comparison.lt)
        GenericFilter.Condition inCondition = new GenericFilter.Condition("account", ["vdanyliuk", "ovovchuk"], GenericFilter.Comparison.in)
        
        GenericFilter.PageRequestImpl pageRequest = new GenericFilter.PageRequestImpl(50, 0, new Sort(new Sort.Order(Sort.Direction.ASC, "amount")))

        filter = new GenericFilter<>([eqCondition, gtCondition, ltCondition, inCondition], pageRequest)

        GenericFilter.PageRequestImpl pageRequestWithoutOrdering = new GenericFilter.PageRequestImpl()
        pageRequestWithoutOrdering.page = 0
        pageRequestWithoutOrdering.size = 50
        filterWithoutOrdering = new GenericFilter<>([eqCondition], pageRequestWithoutOrdering)

        myTxsPageRequest = new GenericFilter.PageRequestImpl(50, 0, new Sort(new Sort.Order(Sort.Direction.ASC, "amount")))

        GenericFilter.Condition wrongCondition = new GenericFilter.Condition("amount", "notNumericValue", GenericFilter.Comparison.eq)

        filterWithWrongType = new GenericFilter([wrongCondition], pageRequestWithoutOrdering)
    }

    @Test
    void 'test of GET request to /api/v1/transactions endpoint'() {
        mockMvc.perform(RestDocumentationRequestBuilders.post('/api/v1/transactions')
                .content(json(filter))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + prepareToken(Collections.emptySet(), "ROLE_BILLING"))
                .contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("content[0].transactionStoring").doesNotExist())
                .andDo(document("txs-filter-request", preprocessRequest(prettyPrint()),
                requestFields(
                        fieldWithPath("conditions").description("All conditions").type(JsonFieldType.OBJECT),
                        fieldWithPath("conditions[0].field").description("Field name for this condition").type(JsonFieldType.STRING),
                        fieldWithPath("conditions[0].value").description("Field value").type(JsonFieldType.VARIES),
                        fieldWithPath("conditions[0].comparison").description("Name of comparing method (eq, gt, lt, in)").type(JsonFieldType.STRING),
                        fieldWithPath("pageable").description("Page information").type(JsonFieldType.OBJECT),
                        fieldWithPath("pageable.size").description("Required page size").type(JsonFieldType.NUMBER),
                        fieldWithPath("pageable.page").description("Page number").type(JsonFieldType.NUMBER),
                        fieldWithPath("pageable.sort").description("Sorting options").type(JsonFieldType.ARRAY),
                        fieldWithPath("pageable.sort[0].direction").description("Sort direction (ASC, DESC)").type(JsonFieldType.STRING),
                        fieldWithPath("pageable.sort[0].property").description("Field to sort by").type(JsonFieldType.STRING),
                        fieldWithPath("pageable.sort[0].ignoreCase").description("Field to sort by").type(JsonFieldType.BOOLEAN).optional(),
                        fieldWithPath("pageable.sort[0].nullHandling").description("What to do with null values (NATIVE, NULLS_FIRST, NULLS_LAST)").type(JsonFieldType.STRING).optional(),
                        fieldWithPath("pageable.sort[0].ascending").type(JsonFieldType.STRING).ignored()
                )))
                .andDo(document('txs-filter-response',
                preprocessResponse(prettyPrint()),
                responseFields(
                        fieldWithPath("content").description("All transactions in this page"),
                        fieldWithPath("content[0].id").description("Transaction id"),
                        fieldWithPath("content[0].account").description("Account that created tx (can be ldap name or any merchant name)"),
                        fieldWithPath("content[0].destination").description("Account that received funds (can be ldap name or any merchant name, empty for treasury)"),
                        fieldWithPath("content[0].comment").description("Comment on transaction"),
                        fieldWithPath("content[0].created").description("Time when tx was created"),
                        fieldWithPath("content[0].amount").description("Transaction funds amount"),
                        fieldWithPath("content[0].status").description("transaction status (SUCCESSFUL or FAILED)"),
                        fieldWithPath("content[0].error").description("Error description if tx fas failed"),
                        fieldWithPath("last").description("Is page last"),
                        fieldWithPath("totalPages").description("Pages quantity"),
                        fieldWithPath("totalElements").description("Elements quantity"),
                        fieldWithPath("sort").description("Sorting"),
                        fieldWithPath("first").description("Is page first"),
                        fieldWithPath("numberOfElements").description("The number of elements currently on this page"),
                        fieldWithPath("size").description("The size of the page"),
                        fieldWithPath("number").description("The number of the current page")
                )))
    }

    @Test
    void 'test of GET request to /api/v1/transactions endpoint without ordering'() {
        mockMvc.perform(RestDocumentationRequestBuilders.post('/api/v1/transactions')
                .content(json(filterWithoutOrdering))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + prepareToken(Collections.emptySet(), "ROLE_BILLING"))
                .contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(status().isOk())
    }

    @Test
    void 'test of GET request to /api/v1/transactions endpoint wrong request and expect resonable message'() {
        mockMvc.perform(RestDocumentationRequestBuilders.post('/api/v1/transactions')
                .content(json(filterWithWrongType))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + prepareToken(Collections.emptySet(), "ROLE_BILLING"))
                .contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(content().json("{\"title\":\"Error\",\"detail\":\"Can't create instance of class BigDecimal from value notNumericValue.\",\"code\":40003,\"developerMessage\":\"java.lang.IllegalArgumentException\"}"))
    }

    @Test
    void 'test of GET request to /api/v1/transactions endpoint with insufficient partitions'() {
        mockMvc.perform(RestDocumentationRequestBuilders.post('/api/v1/transactions')
                .content(json(filter))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + prepareToken(Collections.emptySet(), "ROLE_USER"))
                .contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(status().isForbidden())
    }

    @Test
    void 'test of GET request to /api/v1/transactions/{id} endpoint'() {
        mockMvc.perform(RestDocumentationRequestBuilders.get('/api/v1/transactions/{id}', "10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + prepareToken(Collections.emptySet(), "ROLE_BILLING"))
                .contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("transactionStoring").exists())
                .andDo(document('txs-get-one',
                preprocessResponse(prettyPrint()),
                responseFields(
                        fieldWithPath("id").description("Transaction id"),
                        fieldWithPath("account").description("Account that created tx (can be ldap name or any merchant name)"),
                        fieldWithPath("destination").description("Account that received funds (can be ldap name or any merchant name, empty for treasury)"),
                        fieldWithPath("comment").description("Comment on transaction"),
                        fieldWithPath("created").description("Time when tx was created"),
                        fieldWithPath("amount").description("Transaction funds amount"),
                        fieldWithPath("status").description("transaction status (SUCCESSFUL or FAILED)"),
                        fieldWithPath("error").description("Error description if tx fas failed"),
                        fieldWithPath("remain").description("Error description if tx fas failed").ignored(),
                        fieldWithPath("transactionStoring").description("Additional information about transaction from blockchain"),
                        fieldWithPath("transactionStoring.chainId").description("Name of the Eris chain"),
                        fieldWithPath("transactionStoring.time").description("Time when tx was created in the chain"),
                        fieldWithPath("transactionStoring.txId").description("Transaction id in the chain"),
                        fieldWithPath("transactionStoring.transaction").description("Additional transaction data"),
                        fieldWithPath("transactionStoring.callingValue").description("Values that was used to make call to the smart contract")
                )))
    }

    @Test
    void 'test of GET request to /api/v1/transactions/{id} endpoint wrong role'() {
        mockMvc.perform(RestDocumentationRequestBuilders.get('/api/v1/transactions/{id}', "10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + prepareToken(Collections.emptySet(), "ROLE_USER"))
                .contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(status().isForbidden())
    }

    @Test
    void 'test of GET request to /api/v1/transactions/my endpoint'() {
        mockMvc.perform(RestDocumentationRequestBuilders.get('/api/v1/transactions/my')
                .param("page", "0")
                .param("size", "25")
                .param("sort", "created,asc", "remain,desc")
                .param("direction", "IN")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + prepareToken(Collections.emptySet(), "ROLE_USER"))
                .contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(status().isOk())
                .andDo(document("txs-my-request", preprocessRequest(prettyPrint()),
                requestParameters(
                        parameterWithName("size").description("Required page size").optional(),
                        parameterWithName("page").description("Page number").optional(),
                        parameterWithName("sort").description("Sorting options").optional(),
                        parameterWithName("direction").description("Select incoming, outgoing or all transactions. (IN, OUT, ALL)").optional()
                )))
                .andDo(document('txs-my-response',
                preprocessResponse(prettyPrint()),
                responseFields(
                        fieldWithPath("content").description("All transactions in this page"),
                        fieldWithPath("content[0].id").description("Transaction id"),
                        fieldWithPath("content[0].account").description("Account that created tx (can be ldap name or any merchant name)"),
                        fieldWithPath("content[0].destination").description("Account that received funds (can be ldap name or any merchant name, empty for treasury)"),
                        fieldWithPath("content[0].comment").description("Comment on transaction"),
                        fieldWithPath("content[0].created").description("Time when tx was created"),
                        fieldWithPath("content[0].amount").description("Transaction funds amount"),
                        fieldWithPath("content[0].status").description("transaction status (SUCCESSFUL or FAILED)"),
                        fieldWithPath("content[0].error").description("Error description if tx fas failed"),
                        fieldWithPath("last").description("Is page last"),
                        fieldWithPath("totalPages").description("Pages quantity"),
                        fieldWithPath("totalElements").description("Elements quantity"),
                        fieldWithPath("sort").description("Sorting"),
                        fieldWithPath("first").description("Is page first"),
                        fieldWithPath("numberOfElements").description("The number of elements currently on this page"),
                        fieldWithPath("size").description("The size of the page"),
                        fieldWithPath("number").description("The number of the current page")
                )))
    }

    @Test
    void 'test of GET request to /api/v1/transactions/my endpoint without auth'() {
        mockMvc.perform(RestDocumentationRequestBuilders.get('/api/v1/transactions/my')
                .contentType(MediaType.APPLICATION_JSON_UTF8))
                .andExpect(status().isUnauthorized())
    }


    private static String json(Object o) throws IOException {
        return new ObjectMapper().writeValueAsString(o)
    }

    private String prepareToken(Set<String> scopes, String... authorities) {

        def authoritiesCollection = Arrays.asList(authorities).stream().map({ s ->
            (GrantedAuthority) { -> s
            }
        }).collect(Collectors.toList())
        OAuth2Request oauth2Request = new OAuth2Request(null, "test", authoritiesCollection, true, scopes, null, null, null, null)
        Authentication userAuth = new TestingAuthenticationToken("test", null, authorities)
        OAuth2Authentication oauth2auth = new OAuth2Authentication(oauth2Request, userAuth)
        JwtAccessTokenConverter converter = new JwtAccessTokenConverter()
        KeyPair keyPair = new KeyStoreKeyFactory(
                new ClassPathResource(authKeyFileName), authKeyStorePass.toCharArray())
                .getKeyPair(authKeyAlias, authKeyMasterPass.toCharArray())
        converter.setKeyPair(keyPair)

        tokenService.setTokenEnhancer(converter)
        OAuth2AccessToken token = tokenService.createAccessToken(oauth2auth)
        return token.getValue()
    }
}
