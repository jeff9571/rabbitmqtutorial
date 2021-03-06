			
	本章将创建一个工作队列（Work Queue），用于在多个worker中分配耗时任务。
	工作队列背后的思想是：避免马上执行资源密集的任务，必须等待任务被执行完才执行（假如任务在被其他worker调用，等它调用完才执行）。
	我们将一个任务（task）封装成消息，然后发送到队列。一个后台工作进程会取出任务并最终执行工作。当你运行多个worker，任务会被共享。				
	这个概念特别适合web应用，因为web应用不可能在一个短HTTP请求创空中处理一个复杂的任务。
	
	上篇教程我们发送一个“Hello World！”字符串，现在我们要发送的字符串代表复杂的任务	。实际上并不是像图片大小调整或pdf文件渲染这样的真实任务；
	用Thread.sleep()来假装任务繁忙。我们把字符串中的.当做复杂度；每一个.当作一秒的工作，例如：Hello...是耗时3秒的任务。
	稍微改一下Send.java，让它可以接收命令行输入的参数。这个程序将把任务调度到我们的工作队列中，将其命名为NewTask.java。
	
		String message = String.join(" ", argv);
		channel.basicPublish("", "hello", null, message.getBytes());
		System.out.println(" [x] Sent '" + message + "'");
			
	旧的Recv.java也需要修改，把消息体中的.当做一秒的工作，它会处理传递过来的消息并执行任务，将它命名为Worker.java			
		DeliverCallback deliverCallback = (consumerTag, delivery) -> {
		  String message = new String(delivery.getBody(), "UTF-8");

		  System.out.println(" [x] Received '" + message + "'");
		  try {
			doWork(message);
		  } finally {
			System.out.println(" [x] Done");
		  }
		};
		boolean autoAck = true; // acknowledgment is covered below
		channel.basicConsume(TASK_QUEUE_NAME, autoAck, deliverCallback, consumerTag -> { });		
						
		任务中模拟执行时间
		private static void doWork(String task) throws InterruptedException {
			for (char ch: task.toCharArray()) {
				if (ch == '.') Thread.sleep(1000);
			}
		}		
	先开启两个接收端，再从发送端发送多条消息。		
	RabbitMQ默认将每条小心按顺序发送到下一个消费者，平均每个消费者会收到相同数量的消息（消息平均分给所有消费者），这种分配消息的方式叫做轮询（round-robin）。
	
	“消息确认”
	执行任务可能耗时几秒，如果一个消费者执行一个耗时长的任务，执行到一般挂掉了怎么办。
	现在的代码，一旦RabbitMQ发送消息到消费者，就马上把消息标记为删除。有种情况，如果你杀掉一个正在处理消息的worker，这个处理到一半的消息会丢失掉，而且某些指定这个worker处理的消息也将没人处理。
	但我们不想丢失任何任务，如果worker挂掉，我们想这个任务交给别的worker执行。
	为了确保消息不丢失，RabbitMQ支持消息确认（message acknowledgments），消费者会发送一个确认标记（acknowledgment）给RabbitMQ，告诉它消息被消费了，那样RabbitMQ就可以删除消息了。
	如果使用者在没有发送ack的情况下挂掉(它的通道关闭了，连接关闭了，或者TCP连接丢失了)，RabbitMQ将理解消息没有被完全处理，并将其重新排队。如果同时有其他消费者在线，它就会迅速地将其重新发送给其他消费者。这样你就可以确保没有信息丢失，即使worker偶尔会挂掉。
	不会出现任何消息超时，消费者挂掉，RabbitMQ会重新发送消息（即使是耗时很长的任务）。
	确认（acknowledgment、ack）默认是开启的，上一个例子，我们通过设置autoAck=true标识显式关闭，现在当执行完任务后设置为false并从worker发送合适的acknonwledgment
	
	channel.basicQos(1); // accept only one unack-ed message at a time (see below)

	DeliverCallback deliverCallback = (consumerTag, delivery) -> {
	  String message = new String(delivery.getBody(), "UTF-8");

	  System.out.println(" [x] Received '" + message + "'");
	  try {
		doWork(message);
	  } finally {
		System.out.println(" [x] Done");
		channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
	  }
	};
	boolean autoAck = false;
	channel.basicConsume(TASK_QUEUE_NAME, autoAck, deliverCallback, consumerTag -> { });			
	改后的代码可以确认即使通过ctrl+c杀掉正在处理消息的worker进程，不会丢失任何东西，worker挂掉后很快所有未确认的消息会被重新投递。
	确认必须在接收传递的同一通道上发送。尝试使用不同的通道进行确认将导致通道级协议异常。			
		遗忘acknowledgment
			忽视basicAck后果很严重，消息会被重新投递当客户端程序退出，当不能释放未确认消息，RabbitMQ会吃掉越来越多内存。
			可以通过命令行rabbitmqctl.bat list_queues name messages_ready messages_unacknowledged查看
	
	消息的生命周期
		我们已经学会了如何确保即使消费者挂了，任务也不会丢失。但是如果RabbitMQ服务器停止，我们的任务仍然会丢失。
		当RabbitMQ退出或崩溃时，它将忘记队列和消息，除非你告诉它不要这样做。要确保消息不丢失，需要做两件事:我们需要将队列和消息标记为持久的。
		首先，我们需要确保队列在RabbitMQ节点重新启动时能够存活。为此，我们需要将其声明为持久的。
		boolean durable = true;
		channel.queueDeclare("hello", durable, false, false, null);
	虽然这个命令本身是正确的，但当前的配置不会奏效，因为已经定义了一个hello队列，这个hello队列不是持久化的。
	RabbitMQ不允许用不同的参数重新定义已存在的队列并返回一个错误，不过有个变通方法，定义一个不同名称的队列如：task_queue
		boolean durable = true;
		channel.queueDeclare("task_queue", durable, false, false, null);
	现在修改了queueDeclare会涉及到生产者和消费者一起修改。
	这时候我们可以确定task_queue队列不会丢失，即使RabbitMQ重启。现在我们需要标志消息持久化，通过设置MessageProperies（是BasicProperties的实现）为PERSISTENT_TEXT_PLAIN。
		注意：标志消息持久化不能完全保证消息不会丢失，即使它告诉RabbitMQ把消息保存在磁盘，因为然后有一小段时间空窗RabbitMQ接收到了消息但还没保存。同时RabbitMQ没有为每条消息执行fsync(2)--这可能只是保存在缓存而没写到磁盘。
			这持久化不能确保奏效，但比只做简单task queue（任务队列）要好多了，如果想有更进一步保障持久化，你可以使用publisher confirms。
		
	“公平调度”
		现在的消息调度方式可能不适合实际情况，例如：一种情况是两个worker，所以基数消息都是重量级，偶数消息都是轻量级，一个worker一直执行繁忙的任务而另一个worker基本不做什么，RabbitMQ不知道这种特殊情况，所以依旧把消息平均分配。
		之所以发生这种情况是因为当有消息进入队列，RabbitMQ直接调度消息，它不会帮消费者看未确认的消息数量，只是盲目的把消息调度出去。
		为了解决这种问题，可以使用basicQos方法，设置预取数量（prefetchCount）为1，这会告诉RabbitMQ一次性不要给超过一条信息。换言之，在worker执行完和确认完上一条消息才调度新的消息过来（不然会先调度给下一个不忙的worker）。
		
	关于队列的容量
	如果所有worker繁忙，队列会被填满，需要观察队列容量，可以添加更多worker或者使用其他策略来解决。
	
