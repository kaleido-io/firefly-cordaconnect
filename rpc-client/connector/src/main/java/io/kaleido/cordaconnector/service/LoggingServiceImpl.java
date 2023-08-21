package io.kaleido.cordaconnector.service;

import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import io.kaleido.cordaconnector.logging.LoggingService;
import io.kaleido.cordaconnector.model.request.ConnectorRequest;
import io.kaleido.cordaconnector.model.response.ConnectorResponse;

@Service
public class LoggingServiceImpl implements LoggingService {

  private Logger logger = LoggerFactory.getLogger("LoggingServiceImpl");

  @Override
  public void displayReq(HttpServletRequest request, Object body) {
    StringBuilder reqMessage = new StringBuilder();
    Map<String, String> parameters = getParameters(request);

    reqMessage.append(request.getMethod()).append(" ");
    reqMessage.append(request.getRequestURI()).append(" ");

    if (!parameters.isEmpty()) {
      reqMessage.append("parameters=[").append(parameters).append("] ");
    }

    if (!Objects.isNull(body)) {
      reqMessage.append("requestId=[").append(((ConnectorRequest<?>) body).getId()).append("]");
    }

    logger.info("==> {}", reqMessage);
  }

  @Override
  public void displayResp(HttpServletRequest request, HttpServletResponse response, Object body) {
    StringBuilder respMessage = new StringBuilder();
    Map<String, String> headers = getHeaders(response);
    respMessage.append(request.getMethod()).append(" ");
    respMessage.append(request.getRequestURI()).append(" ");
    respMessage.append("[").append(response.getStatus()).append("] ");
    if (!headers.isEmpty()) {
      respMessage.append("headers=[").append(headers).append("] ");
    }
    respMessage.append("body=[").append(body).append("]");

    logger.info("<== {}", respMessage);
  }

  private Map<String, String> getHeaders(HttpServletResponse response) {
    Map<String, String> headers = new HashMap<>();
    Collection<String> headerMap = response.getHeaderNames();
    for (String str : headerMap) {
      headers.put(str, response.getHeader(str));
    }
    return headers;
  }

  private Map<String, String> getParameters(HttpServletRequest request) {
    Map<String, String> parameters = new HashMap<>();
    Enumeration<String> params = request.getParameterNames();
    while (params.hasMoreElements()) {
      String paramName = params.nextElement();
      String paramValue = request.getParameter(paramName);
      parameters.put(paramName, paramValue);
    }
    return parameters;
  }

}