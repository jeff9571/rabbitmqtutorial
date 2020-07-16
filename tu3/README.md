
	上一章创建了工作队列，工作队列背后的设想是每个任务能精确地被投递到一个worker。这一章则完全不同--我们发送一条消息给多个消费者，这种模式就叫做“发布/订阅”。
	为了说明这个模式，我们将构建一个简单的日志记录系统。它将由两个程序组成——第一个将发送日志消息，第二个将接收和打印它们。
	在本日志系统，每个运行的接收者都会收到消息。这样就可以运行一个接收者并将日志写到磁盘；同时可以运行其他接收者能看到屏幕打印的日志。
	基本上，发布的日志消息将广播到所有接收方。
	
	交换（exchanges）
	前几部分中，我们向队列发送和接收消息。现在介绍Rabbit中的完整消息传递模型。
	让我们快速回顾一下在前面的教程中涉及到的内容：
		producer是发送消息的用户应用程序。
		queue是存储消息的缓冲区。
		consumer是接收消息的用户应用程序。
	RabbitMQ消息模型的核心是生产者从不直接把消息发送给队列，实际上生产者通常根本不知道一条消息是否被发送到哪条队列。
	相反，生产者只能把消息发给交换机（exchange）。交换机很简单，一方面从生产者接收消息，另一方面把消息推到队列。交换机必须知道接收到消息应该做什么,是发到指定队列？发到多个队列？丢弃？这规则有交换机类型来定义。
	有几种可用的交换类型:direct、topic、header和fanout。我们将专注于最后一个--fanout。让我们创建一个这种类型的交换器，并将其称为logs	
	fanout模式从命名上都可以看出，它就是广播所有接收到的消息到它知道的队列，这正是我们的logger所需要的。	
		
	通过rabbitmqctl list_exchanges列出所有交换类型，可能列出来的有些是amqp.*包下的，都是默认创建的。现在还用不上。
	
	匿名的交换机（nameless exchage）
	***前面的教程我们对交换机一无所知，但依然可以发送消息到队列，那是因为我们用了默认的交换机类型，用""（空字符串）表示使用默认类型。	
	channel.basicPublish("", "hello", null, message.getBytes());
	第一个参数是交换机名称，空字符串表示默认类型或者匿名交换机，消息被路由到队列（如果有指定路由键则路由到指定队列）。
		现在可以指定名称
		channel.basicPublish( "logs", "", null, message.getBytes());
		
	临时队列（temporary queues）
	还记得之前用hello和task_queue作为队列名？命名队列很重要--我们需要将工人指向相同的队列。当您希望在生产者和消费者之间共享队列时，为队列提供一个名称是很重要的。
	我们需要所有消息，而不是队列中的其中一条；同时我们只对当前活跃（flowing）的消息感兴趣，而不是旧的消息，为此需要做到两点：
		1、连接RabbitMQ时需要一个新的、空的队列，可以让RabbitMQ server随机创建一个。
		2、关闭消费者时，队列应该自动删除。
		
	无参数的queueDeclare()我们创建了一个非持久的、排他的、自动删除队列，并生成了一个名称:
	String queueName = channel.queueDeclare().getQueue();
	此时queueName包含一个随机队列名称。例如，它可能看起来像amq.gen- jzty20brgko - hjmuj0wlg
	
	绑定（binding）
	现在创建了fanout交换机和队列，现在告诉交换机发送消息给队列，交换机和队列的关系叫做 绑定。
	channel.queueBind(queueName, "logs", "");
	现在logs交换机会追加方式发消息到队列。
	执行rabbitmqctl list_bindings列出所有绑定。
	
	消费者负责发送日志消息，跟上一章例子最大的区别是，现在发布消息到logs交换机，而不是匿名的交换机，发送消息时需要提供一个路由键，但fanout类型会忽略它的值。
	public class EmitLog {

	  private static final String EXCHANGE_NAME = "logs";

	  public static void main(String[] argv) throws Exception {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		try (Connection connection = factory.newConnection();
			 Channel channel = connection.createChannel()) {
			channel.exchangeDeclare(EXCHANGE_NAME, "fanout");

			String message = argv.length < 1 ? "info: Hello World!" :
								String.join(" ", argv);

			channel.basicPublish(EXCHANGE_NAME, "", null, message.getBytes("UTF-8"));
			System.out.println(" [x] Sent '" + message + "'");
		}
	  }
	}
	建立连接后我们就声明交换机，这一步是必须的，因为发布消息到不存在的交换机是不允许的。
	如果没有队列绑定到交换，消息将丢失，但这对我们来说没问题;如果还没有消费者在听，我们就可以安全地丢弃这些信息。
	import com.rabbitmq.client.Channel;
	import com.rabbitmq.client.Connection;
	import com.rabbitmq.client.ConnectionFactory;
	import com.rabbitmq.client.DeliverCallback;

	public class ReceiveLogs {
	  private static final String EXCHANGE_NAME = "logs";

	  public static void main(String[] argv) throws Exception {
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost("localhost");
		Connection connection = factory.newConnection();
		Channel channel = connection.createChannel();

		channel.exchangeDeclare(EXCHANGE_NAME, "fanout");
		String queueName = channel.queueDeclare().getQueue();
		channel.queueBind(queueName, EXCHANGE_NAME, "");

		System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

		DeliverCallback deliverCallback = (consumerTag, delivery) -> {
			String message = new String(delivery.getBody(), "UTF-8");
			System.out.println(" [x] Received '" + message + "'");
		};
		channel.basicConsume(queueName, true, deliverCallback, consumerTag -> { });
	  }
	}
	
	
