package main;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class EmitLogTopic {

	 private static final String EXCHANGE_NAME = "topic_logs";

	  public static void main(String[] argv) throws Exception {
	    ConnectionFactory factory = new ConnectionFactory();
	    factory.setHost("localhost");
	    try (Connection connection = factory.newConnection();
	         Channel channel = connection.createChannel()) {

	        channel.exchangeDeclare(EXCHANGE_NAME, "topic");

	        String routingKey = argv[0];
	        String message = argv[1];

	        channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes("UTF-8"));
	        System.out.println(" [x] Sent '" + routingKey + "':'" + message + "'");
	    }
	  }

	public static String getMessage(String... msg) {
		return msg[1];
	}

	public static String getRouting(String... severity) {
		String result = "";

		switch (severity[0]) {
		case "INFO":
			result = "INFO";
			break;
		case "ERROR":
			result = "ERROR";
			break;
		default:
			break;
		}
		return result;
	}
}