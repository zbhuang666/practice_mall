package com.zhs.zhsmall.config;

import com.zhs.zhsmall.enhancer.ZhsTokenEnhancer;
import com.zhs.zhsmall.service.ZhsUserDetailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.config.annotation.configurers.ClientDetailsServiceConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configuration.AuthorizationServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableAuthorizationServer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerEndpointsConfigurer;
import org.springframework.security.oauth2.config.annotation.web.configurers.AuthorizationServerSecurityConfigurer;
import org.springframework.security.oauth2.provider.ClientDetailsService;
import org.springframework.security.oauth2.provider.client.JdbcClientDetailsService;
import org.springframework.security.oauth2.provider.token.TokenEnhancer;
import org.springframework.security.oauth2.provider.token.TokenEnhancerChain;
import org.springframework.security.oauth2.provider.token.TokenStore;
import org.springframework.security.oauth2.provider.token.store.JwtAccessTokenConverter;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableAuthorizationServer
public class ZhsAuthorizationServerConfig extends AuthorizationServerConfigurerAdapter {

    @Autowired
    @Qualifier("jwtTokenStore")
    private TokenStore tokenStore;

    @Autowired
    private JwtAccessTokenConverter jwtAccessTokenConverter;

    @Resource
    DataSource dataSource;

    @Resource
    PasswordEncoder passwordEncoder;

    @Resource
    AuthenticationManager authenticationManager;

    @Resource
    private ZhsUserDetailService zhsUserDetailService;

    @Resource
    private ZhsTokenEnhancer zhsTokenEnhancer;

    @Override
    public void configure(AuthorizationServerSecurityConfigurer security) throws Exception {
        security.checkTokenAccess("isAuthenticated()").tokenKeyAccess("isAuthenticated()");
    }

    @Override
    public void configure(ClientDetailsServiceConfigurer clients) throws Exception {
        // ?????????????????????????????????????????????????????? ??????DB?????? oauth_client_details
        // clients.withClientDetails(clientDetails());

        /**
         * ???????????????
         * http://localhost:9999/oauth/authorize? response_type=code&client_id=client&redirect_uri=http://www.baidu.com&scope=all
         * password??????
         * http://localhost:8080/oauth/token?username=fox&password=123456&grant_type=password&client_id=client&client_secret=123123&scope=all
         */

        clients.inMemory()
                //??????client_id
                .withClient("client")
                //??????client???secret
                .secret(passwordEncoder.encode("123123"))
                //????????????token????????????
                .accessTokenValiditySeconds(3600)
                //????????????token????????????
                .refreshTokenValiditySeconds(86400)
                //??????redirect_uri??????????????????????????????
                .redirectUris("http://www.baidu.com")
                //???????????????????????????
                .scopes("all")
                /**
                 * ??????grant_type?????????????????????
                 *  authorization_code: ?????????
                 *  password??? ??????
                 *  refresh_token: ????????????
                 */
                .authorizedGrantTypes("authorization_code", "password", "refresh_token");
    }

    @Override
    public void configure(AuthorizationServerEndpointsConfigurer endpoints) throws Exception {
        //??????JWT??????????????????
        TokenEnhancerChain tokenEnhancerChain = new TokenEnhancerChain();
        List<TokenEnhancer> delegates = new ArrayList<>();
        delegates.add(zhsTokenEnhancer);
        delegates.add(jwtAccessTokenConverter);
        tokenEnhancerChain.setTokenEnhancers(delegates);

        //??????????????????????????????
        endpoints.authenticationManager(authenticationManager)
                .tokenStore(tokenStore) //??????token???????????????jwt
                .accessTokenConverter(jwtAccessTokenConverter)
                .tokenEnhancer(tokenEnhancerChain)
                .reuseRefreshTokens(false)  //refresh_token??????????????????
                .userDetailsService(zhsUserDetailService) //????????????????????????????????????????????????
                .allowedTokenEndpointRequestMethods(HttpMethod.GET, HttpMethod.POST); //??????GET,POST??????
    }

    @Bean
    public ClientDetailsService clientDetails(){
        return new JdbcClientDetailsService(dataSource);
    }


}
