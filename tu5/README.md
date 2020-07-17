

	direct交换机还是有局限性，不能基于多重条件进行路由。
	在当前的日志系统我们可能不止想基于日志级别进行订阅，还想基于发送消息的源头来进行订阅。可能你知道unix tool的概念，它可以基于日志级别（info、warn..）以及设备（auth/cron/kern）进行路由
	这样会带来更好灵活性，我们可能只需要监听来自cron的关键错误，也需要监听来自kern的所有日志。
	为了实现这个功能，现在要学习更复杂的topic交换机。
	
	TOPIC Exchange
		发往topic交换机的消息不能是随意的路由键--必须是一组词，用逗号分隔开。词可以是任意的，但通常用来指定信息的某些特征。
		有效的路由键示例:“stock.usd”，“quick.orange.rabbit”。路由键中可以有任意多的单词，最多255字节。
		绑定键也要相同的格式，topic类型和direct有点像--消息带有特定的路由键发送到所有绑定键与之一样的队列上，绑定键有两个重要的特殊用法：
		* 可以代替一个完整的词
		# 可以代替>=0个词
	
		这个例子用动物来表示，消息的路由键由3个词组成，速度、颜色、种类<speed>.<colour>.<species>
		
		如：Q1绑定*.orange.*  Q2绑定*.*.rabbit 和 lazy.#
		可以总结为：Q1关注orange动物；Q2关注所有类型的rabbit和所有lazy的动物
		像orange、quick.orange.male.rabbit这种不合约定规则的消息会丢失掉。其他将按照规则投递到符合规则的队列。
		lazy.orange.male.rabbit尽管有四个词，但是会被投递到Q2队列。
		当队列绑定了#为绑定键，它会接受所有消息，不管消息的路由键是什么，表现就想fanout类型的交换机。
		当绑定中没有*和#，表现就跟direct交换机。

	组合一起
		假设日志系统中的路由键由两个词组成，设备.级别<facility>.<severity>
		发送：
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

				String routingKey = getRouting(argv);
				String message = getMessage(argv);

				channel.basicPublish(EXCHANGE_NAME, routingKey, null, message.getBytes("UTF-8"));
				System.out.println(" [x] Sent '" + routingKey + "':'" + message + "'");
			}
		  }
		  //..
		}
		接收：
		public class ReceiveLogsTopic {

		  private static final String EXCHANGE_NAME = "topic_logs";

		  public static void main(String[] argv) throws Exception {
			ConnectionFactory factory = new ConnectionFactory();
			factory.setHost("localhost");
			Connection connection = factory.newConnection();
			Channel channel = connection.createChannel();

			channel.exchangeDeclare(EXCHANGE_NAME, "topic");
			String queueName = channel.queueDeclare().getQueue();

			if (argv.length < 1) {
				System.err.println("Usage: ReceiveLogsTopic [binding_key]...");
				System.exit(1);
			}

			for (String bindingKey : argv) {
				channel.queueBind(queueName, EXCHANGE_NAME, bindingKey);
			}

			System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

			DeliverCallback deliverCallback = (consumerTag, delivery) -> {
				String message = new String(delivery.getBody(), "UTF-8");
				System.out.println(" [x] Received '" +
					delivery.getEnvelope().getRoutingKey() + "':'" + message + "'");
			};
			channel.basicConsume(queueName, true, deliverCallback, consumerTag -> { });
		  }
		}
		
