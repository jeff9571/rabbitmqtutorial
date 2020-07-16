package main;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;

public class Send {

	public final static String QUEUE_NAME = "task_queue";

	public static void main(String[] args) throws Exception {
		String message = String.join(" ", args);
		
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		//java8在try中打开连接、通道不需要手动关闭，会自动关闭
		try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
//			channel.queueDeclare(QUEUE_NAME, false, false, false, null);
//			channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
//			System.out.println(" [x] Sent '" + message + "'");
			
			boolean durable = true;
			channel.queueDeclare(QUEUE_NAME, durable, false, false, null);
			channel.basicPublish("", QUEUE_NAME,
		            MessageProperties.PERSISTENT_TEXT_PLAIN,
		            message.getBytes());
		}
	}

}
