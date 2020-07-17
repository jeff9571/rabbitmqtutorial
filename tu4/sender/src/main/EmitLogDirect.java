package main;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class EmitLogDirect {

	private static final String EXCHANGE_NAME = "direct_logs";

	public static void main(String[] argv) throws Exception {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
			channel.exchangeDeclare(EXCHANGE_NAME, "direct");
			if (argv.length == 0) {
				return;
			}
			String severity = getSeverity(argv);
			String message = getMessage(argv);

			channel.basicPublish(EXCHANGE_NAME, severity, null, message.getBytes("UTF-8"));
			System.out.println(" [x] Sent '" + severity + "':'" + message + "'");
		}
	}

	public static String getMessage(String... msg) {
		return msg[1];
	}

	public static String getSeverity(String... severity) {
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