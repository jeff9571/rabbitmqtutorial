
	路由（Routing）
		上一章建立一个简单日志系统，可以广播日志信息到很多接收者。
		这章将添加一个新功能，让它能够只订阅消息的子集（subset），如只把严重的错误信息写入到日志文件（保存到磁盘空间），同时其他普通日志消息依旧可以打印在控制台。
	绑定（Binding）
		上一章例子已经创建了绑定，回忆一下：
		channel.queueBind(queueName, EXCHANGE_NAME, "");
		交换机和队列的关系就是 绑定，可以简单理解为：队列关注来自交换机的消息。
		绑定可以带上一个额外的路由键参数，为了避免和basic_publish参数混淆，我们把这个额外的路由键参数叫做“绑定键”，路由键和绑定键本质上是一样的。一般消息绑定交换机叫路由键，交换机绑定队列叫绑定键。
		创建绑定键
		channel.queueBind(queueName, EXCHANGE_NAME, "black");
		绑定键的意义取决于交换机类型，之前用的fanout类型就会直接忽略掉这个参数。
		
	direct交换机（direct exchage）
		我们上一章的日志系统是广播所有消息给消费者，我们想扩展它，让它基于严重程度过滤消息。例如，程序只有严重错误才把消息写入磁盘，其他消息不写，避免浪费磁盘空间。
		之前的fanout类型不灵活，唯一的作用是一味的盲发消息。
		现在使用direct类型替代fanout，direct交换机背后的路由算法很简单--队列的绑定键和消息的路由键相匹配，消息才会进入该队列。
		跟着下面步骤证明：
		1、交换机x和两条队列绑定（Q1 Q2），第一个队列的绑定键是orange，第二队列有两个绑定键black和green。
			这样的设置，一条消息发布到到交换机带的路由键是orange的话会被路由到队列Q1
		
	多重绑定（multiple bindings）
		相同的绑定键绑定多个队列是完全合法的。交换机x用绑定键black和队列Q1、Q2时，发送一条路由键是black的消息，这时候就会像fanout类型的交换机一样，消息会广播到所有队列。
	日志发送（Emitting logs）
		我们用日志级别作为路由键，接收程序根据它需要的级别来接收。
		老规矩，首先创建交换机
		channel.exchangeDeclare(EXCHANGE_NAME, "direct");
		准备好发送消息
		channel.basicPublish(EXCHANGE_NAME, severity, null, message.getBytes());
		这里简化上诉的“级别”，用字符串"info","wanrning","error"表示。
		
	订阅（subscribing）
		跟上一章一样接收消息，不同的是，我们要为每个关注的级别创建新的绑定。
		String queueName = channel.queueDeclare().getQueue();

		for(String severity : argv){
		  channel.queueBind(queueName, EXCHANGE_NAME, severity);
		}
		
		发送端
		import com.rabbitmq.client.Channel;
		import com.rabbitmq.client.Connection;
		import com.rabbitmq.client.ConnectionFactory;

		public class EmitLogDirect {

		  private static final String EXCHANGE_NAME = "direct_logs";

		  public static void main(String[] argv) throws Exception {
			ConnectionFactory factory = new ConnectionFactory();
			factory.setHost("localhost");
			try (Connection connection = factory.newConnection();
				 Channel channel = connection.createChannel()) {
				channel.exchangeDeclare(EXCHANGE_NAME, "direct");

				String severity = getSeverity(argv);
				String message = getMessage(argv);

				channel.basicPublish(EXCHANGE_NAME, severity, null, message.getBytes("UTF-8"));
				System.out.println(" [x] Sent '" + severity + "':'" + message + "'");
			}
		  }
		  //..
		}
		接收端
		public class ReceiveLogsDirect {

		  private static final String EXCHANGE_NAME = "direct_logs";

		  public static void main(String[] argv) throws Exception {
			ConnectionFactory factory = new ConnectionFactory();
			factory.setHost("localhost");
			Connection connection = factory.newConnection();
			Channel channel = connection.createChannel();

			channel.exchangeDeclare(EXCHANGE_NAME, "direct");
			String queueName = channel.queueDeclare().getQueue();

			if (argv.length < 1) {
				System.err.println("Usage: ReceiveLogsDirect [info] [warning] [error]");
				System.exit(1);
			}

			for (String severity : argv) {
				channel.queueBind(queueName, EXCHANGE_NAME, severity);
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
			
		
		只运行接收端
		java -jar recv.jar INFO testmsg (普通消息只打印到console)
		java -jar recv.jar ERROR > log.txt (ERROR输出到日志)
		
