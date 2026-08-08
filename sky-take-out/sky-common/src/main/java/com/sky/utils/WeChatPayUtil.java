package com.sky.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.properties.WeChatProperties;
import com.wechat.pay.contrib.apache.httpclient.WechatPayHttpClientBuilder;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 微信支付工具类（证书启动时加载并缓存）
 */
@Component
@Slf4j
public class WeChatPayUtil {

    //微信支付下单接口地址
    public static final String JSAPI = “https://api.mch.weixin.qq.com/v3/pay/transactions/jsapi”;

    //申请退款接口地址
    public static final String REFUNDS = “https://api.mch.weixin.qq.com/v3/refund/domestic/refunds”;

    @Autowired
    private WeChatProperties weChatProperties;

    /** 缓存的 HTTP 客户端（含签名/验签） */
    private CloseableHttpClient httpClient;

    /** 缓存的商户私钥（二次签名用） */
    private PrivateKey merchantPrivateKey;

    @PostConstruct
    public void init() {
        try (FileInputStream keyStream = new FileInputStream(new File(weChatProperties.getPrivateKeyFilePath()));
             FileInputStream certStream = new FileInputStream(new File(weChatProperties.getWeChatPayCertFilePath()))) {

            this.merchantPrivateKey = PemUtil.loadPrivateKey(keyStream);
            X509Certificate x509Certificate = PemUtil.loadCertificate(certStream);
            List<X509Certificate> wechatPayCertificates = Arrays.asList(x509Certificate);

            this.httpClient = WechatPayHttpClientBuilder.create()
                    .withMerchant(weChatProperties.getMchid(), weChatProperties.getMchSerialNo(), merchantPrivateKey)
                    .withWechatPay(wechatPayCertificates)
                    .build();

            log.info(“微信支付客户端初始化成功”);
        } catch (FileNotFoundException e) {
            log.error(“微信支付证书文件未找到: {}”, e.getMessage(), e);
            throw new RuntimeException(“微信支付证书文件未找到”, e);
        } catch (Exception e) {
            log.error(“微信支付客户端初始化失败: {}”, e.getMessage(), e);
            throw new RuntimeException(“微信支付客户端初始化失败”, e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (httpClient != null) {
            try {
                httpClient.close();
                log.info(“微信支付客户端已关闭”);
            } catch (IOException e) {
                log.warn(“关闭微信支付客户端时异常”, e);
            }
        }
    }

    /**
     * 发送post方式请求
     */
    private String post(String url, String body) throws Exception {
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
        httpPost.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
        httpPost.addHeader(“Wechatpay-Serial”, weChatProperties.getMchSerialNo());
        httpPost.setEntity(new StringEntity(body, “UTF-8”));

        try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
            return EntityUtils.toString(response.getEntity());
        }
    }

    /**
     * 发送get方式请求
     */
    private String get(String url) throws Exception {
        HttpGet httpGet = new HttpGet(url);
        httpGet.addHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.toString());
        httpGet.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.toString());
        httpGet.addHeader(“Wechatpay-Serial”, weChatProperties.getMchSerialNo());

        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            return EntityUtils.toString(response.getEntity());
        }
    }

    /**
     * jsapi下单
     */
    private String jsapi(String orderNum, BigDecimal total, String description, String openid) throws Exception {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(“appid”, weChatProperties.getAppid());
        jsonObject.put(“mchid”, weChatProperties.getMchid());
        jsonObject.put(“description”, description);
        jsonObject.put(“out_trade_no”, orderNum);
        jsonObject.put(“notify_url”, weChatProperties.getNotifyUrl());

        JSONObject amount = new JSONObject();
        amount.put(“total”, total.multiply(new BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_UP).intValue());
        amount.put(“currency”, “CNY”);

        jsonObject.put(“amount”, amount);

        JSONObject payer = new JSONObject();
        payer.put(“openid”, openid);

        jsonObject.put(“payer”, payer);

        return post(JSAPI, jsonObject.toJSONString());
    }

    /**
     * 小程序支付
     */
    public JSONObject pay(String orderNum, BigDecimal total, String description, String openid) throws Exception {
        //统一下单，生成预支付交易单
        String bodyAsString = jsapi(orderNum, total, description, openid);
        JSONObject jsonObject = JSON.parseObject(bodyAsString);
        log.info(“微信支付预下单响应: {}”, jsonObject);

        String prepayId = jsonObject.getString(“prepay_id”);
        if (prepayId != null) {
            String timeStamp = String.valueOf(System.currentTimeMillis() / 1000);
            String nonceStr = RandomStringUtils.randomNumeric(32);
            ArrayList<Object> list = new ArrayList<>();
            list.add(weChatProperties.getAppid());
            list.add(timeStamp);
            list.add(nonceStr);
            list.add(“prepay_id=” + prepayId);

            //二次签名，调起支付需要重新签名
            StringBuilder stringBuilder = new StringBuilder();
            for (Object o : list) {
                stringBuilder.append(o).append(“\n”);
            }
            byte[] message = stringBuilder.toString().getBytes();

            Signature signature = Signature.getInstance(“SHA256withRSA”);
            signature.initSign(merchantPrivateKey);
            signature.update(message);
            String packageSign = Base64.getEncoder().encodeToString(signature.sign());

            //构造数据给微信小程序，用于调起微信支付
            JSONObject jo = new JSONObject();
            jo.put(“timeStamp”, timeStamp);
            jo.put(“nonceStr”, nonceStr);
            jo.put(“package”, “prepay_id=” + prepayId);
            jo.put(“signType”, “RSA”);
            jo.put(“paySign”, packageSign);

            return jo;
        }
        return jsonObject;
    }

    /**
     * 申请退款
     */
    public String refund(String outTradeNo, String outRefundNo, BigDecimal refund, BigDecimal total) throws Exception {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(“out_trade_no”, outTradeNo);
        jsonObject.put(“out_refund_no”, outRefundNo);

        JSONObject amount = new JSONObject();
        amount.put(“refund”, refund.multiply(new BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_UP).intValue());
        amount.put(“total”, total.multiply(new BigDecimal(100)).setScale(2, BigDecimal.ROUND_HALF_UP).intValue());
        amount.put(“currency”, “CNY”);

        jsonObject.put(“amount”, amount);
        jsonObject.put(“notify_url”, weChatProperties.getRefundNotifyUrl());

        return post(REFUNDS, jsonObject.toJSONString());
    }
}
