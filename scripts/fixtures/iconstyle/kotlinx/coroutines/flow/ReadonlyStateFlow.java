package kotlinx.coroutines.flow;public class ReadonlyStateFlow {private final MutableStateFlow delegate;public ReadonlyStateFlow(MutableStateFlow flow){delegate=flow;}}
