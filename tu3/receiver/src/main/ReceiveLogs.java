package main;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

public class ReceiveLogs {
	private static final String EXCHANGE_NAME = "logs";

	public static void main(String[] argv) throws Exception {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		Connection connection = factory.newConnection();//新建连接
		Channel channel = connection.createChannel();//通过连接创建通道

		channel.exchangeDeclare(EXCHANGE_NAME, "fanout");//通过通道声明交换机名称和类型
		String queueName = channel.queueDeclare().getQueue();//随机生成队列名
		channel.queueBind(queueName, EXCHANGE_NAME, "");//绑定交换机和队列

		System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

		DeliverCallback deliverCallback = (consumerTag, delivery) -> {
			String message = new String(delivery.getBody(), "UTF-8");
			System.out.println(" [x] Received '" + message + "'");
		};
		channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {
		});
	}
}