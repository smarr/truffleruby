# Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 1.0
# GNU General Public License version 2
# GNU Lesser General Public License version 2.1

# Copyright (c) 2007-2015, Evan Phoenix and contributors
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of Rubinius nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

# From https://github.com/rubinius/rubinius/blob/v2.71828182/rakelib/platform.rake
# and its required files, but greatly simplified.

PREFIX = 'rbx.platform'
ROOT = File.expand_path('../..', __FILE__)
SCRIPT = File.expand_path(__FILE__)[ROOT.size+1..-1]

EXTRA_CFLAGS = ''

case RUBY_PLATFORM
when /x86_64-linux/
  PLATFORM_FILE = 'org/truffleruby/platform/linux/LinuxRubiniusConfiguration.java'
  EXTRA_CFLAGS << ' -D_GNU_SOURCE'
when /x86_64-darwin/
  PLATFORM_FILE = 'org/truffleruby/platform/darwin/DarwinRubiniusConfiguration.java'
when /sparcv9-solaris/
  PLATFORM_FILE = 'org/truffleruby/platform/solaris/SolarisSparcV9RubiniusConfiguration.java'
  ENV['CC'] ||= 'gcc'
  # "-m64" forces a 64-bit binary
  # "-D_XOPEN_SOURCE=600" tells Solaris to use the SUSv3 feature set
  # "-std=gnu99" is required to build with SUSv3 enabled
  EXTRA_CFLAGS << ' -std=gnu99 -m64 -D_XOPEN_SOURCE=600 -D__EXTENSIONS__=1'
else
  raise "Unknown platform #{RUBY_PLATFORM}"
end

class ConfigFileHandler
  CONFIG_FILE = File.expand_path('../platform.conf', __FILE__)

  def initialize
    @file = File.open(CONFIG_FILE, 'wb')
    at_exit { @file.close }
  end

  def register(key, value)
    @file.puts "#{PREFIX}.#{key} = #{value}"
  end
end

class JavaHandler
  METHOD_START = "public static void load(RubiniusConfiguration configuration, RubyContext context) {\n"
  METHOD_END = "    }\n\n}"
  INDENT = ' '*8

  def initialize
    project = "#{ROOT}/src/main/java"
    java_file = File.join(project, PLATFORM_FILE)
    contents = File.read(java_file)
    from = contents.index(METHOD_START)
    raise "Could not find start in #{java_file}" unless from
    to = contents.index(METHOD_END)
    raise "Could not find end in #{java_file}" unless to

    @file = File.open(java_file, 'wb')
    @file.puts contents[0...from+METHOD_START.length]
    @file.puts "#{INDENT}// Generated from #{SCRIPT} on #{RUBY_PLATFORM}"
    at_exit do
      @file.puts contents[to..-1]
      @file.close
    end
  end

  def register(key, value)
    code = case value
    when Integer
      case value
      when (-2**31...2**31)
        value
      when (-2**63...2**63)
        "#{value}L"
      else
        "newBignum(context, \"#{value}\")"
      end
    when 'true'
      value
    else
      "string(context, \"#{value}\")"
    end
    var = "#{PREFIX}.#{key}"
    @file.puts "#{INDENT}configuration.config(#{var.inspect}, #{code});"
  end
end

HANDLER = JavaHandler.new

def run(command)
  puts "$ #{command}"
  output = `#{command}`
  raise "#{command} failed:\m#{output}" unless $?.success?
  output
end

class Generator
  def initialize
    @includes = []
  end

  def include(header)
    @includes << header
  end

  def cc
    ENV.fetch('CC', 'cc')
  end

  def compile_command(source, target)
    cflags = "-xc -Wall -Werror#{EXTRA_CFLAGS}"
    "#{cc} #{cflags} #{source} -o #{target} 2>&1"
  end

  def execute_command(source, target)
    target
  end

  def compile(file)
    target = file[0...-2]
    run compile_command(file, target)
    begin
      run execute_command(file, target)
    ensure
      File.delete target if File.exist?(target)
    end
  end

  def generate
    file = File.expand_path('../config.c', __FILE__)
    begin
      File.open(file, 'wb') { |f| source(f) }
      analyse(compile(file))
      save
    ensure
      File.delete(file)
    end
  end

  def register(key, value)
    HANDLER.register(key, value)
  end
end

class StructGenerator < Generator
  Field = Struct.new(:name, :type, :offset, :size)

  def initialize(name)
    super()
    @name = name
    @fields = []
  end

  def field(name, type=nil)
    @fields << Field.new(name, type)
  end

  def source(io)
    io.puts '#include <stdio.h>'
    @includes.each do |inc|
      io.puts "#include <#{inc}>"
    end
    io.puts "#include <stddef.h>\n\n"

    io.puts 'int main(int argc, char **argv) {'
    io.puts "  struct #{@name} s;"
    io.puts %{  printf("sizeof(struct #{@name}) %u\\n", (unsigned int) sizeof(struct #{@name}));}
    @fields.each do |field|
      io.puts %{  printf("#{field.name} %u %u\\n", } +
        "(unsigned int) offsetof(struct #{@name}, #{field.name}), " +
        "(unsigned int) sizeof(s.#{field.name}));"
    end
    io.puts "\n  return 0;\n}"
  end

  def analyse(output)
    output = output.lines
    line = output.shift
    size = line[/^sizeof\(struct #{@name}\) (\d+)$/, 1]
    raise line unless size
    @size = Integer(size)

    @fields.each do |field|
      line = output.shift
      raise line unless line[/^#{field.name} (\d+) (\d+)$/]
      field.offset = Integer($1)
      field.size = Integer($2)
    end
  end

  def save
    register "#{@name}.sizeof", @size
    @fields.each do |field|
      register "#{@name}.#{field.name}.offset", field.offset
      register "#{@name}.#{field.name}.size", field.size
      register "#{@name}.#{field.name}.type", field.type if field.type
    end
  end
end

class ConstantsGenerator < Generator
  Constant = Struct.new(:name, :format, :cast, :value)

  def initialize(group)
    super()
    @group = group
    @constants = {}
  end

  def consts(names, format = '%ld', cast = '(long)')
    names.each do |name|
      @constants[name] = Constant.new(name, format, cast, nil)
    end
  end

  def source(io)
    io.puts '#include <stdio.h>'
    @includes.each do |inc|
      io.puts "#include <#{inc}>"
    end
    io.puts "#include <stddef.h>\n\n"

    io.puts 'int main(int argc, char **argv) {'
    @constants.each_value do |const|
      io.puts <<-EOC
  #ifdef #{const.name}
  printf("#{const.name} #{const.format}\\n", #{const.cast}#{const.name});
  #endif
      EOC
    end
    io.puts "\n  return 0;\n}"
  end

  def analyse(output)
    output.each_line do |line|
      raise line unless line =~ /^(\w+) (.*)$/
      name, value = $1, $2
      value = Integer(value) if /^-?(\d)+$/ =~ value
      @constants[name].value = value
    end
  end

  def save
    @constants.each_pair do |name, constant|
      if constant.value
        register "#{@group}.#{name}", constant.value
      end
    end
  end
end

class TypesGenerator < Generator
  # Maps C types to the C type representations we use
  TYPE_MAP = {
             'char' => :char,
      'signed char' => :char,
    '__signed char' => :char,
    'unsigned char' => :uchar,

             'short'     => :short,
             'short int' => :short,
      'signed short'     => :short,
      'signed short int' => :short,
    'unsigned short'     => :ushort,
    'unsigned short int' => :ushort,

             'int' => :int,
      'signed int' => :int,
    'unsigned int' => :uint,

             'long'     => :long,
             'long int' => :long,
      'signed long'     => :long,
      'signed long int' => :long,
    'unsigned long'     => :ulong,
    'unsigned long int' => :ulong,
    'long unsigned int' => :ulong,

             'long long'     => :long_long,
             'long long int' => :long_long,
      'signed long long'     => :long_long,
      'signed long long int' => :long_long,
    'unsigned long long'     => :ulong_long,
    'unsigned long long int' => :ulong_long,

    'char *' => :string,
    'void *' => :pointer,
  }

  def initialize
    super()
    @typedefs = {}
  end

  def source(io)
    io.puts '#include <stdint.h>'
    io.puts '#include <sys/types.h>'
    io.puts '#include <sys/socket.h>'
    io.puts '#include <sys/resource.h>'
  end

  def compile_command(source, target)
    'echo typedefs'
  end

  def execute_command(source, target)
    "#{cc} -E#{EXTRA_CFLAGS} #{source}"
  end

  def analyse(output)
    output.lines.select { |line|
      line =~ /typedef/
    }.reject { |line|
      line =~ /\b(union|struct|enum)\b/
    }.each { |line|
      line.chomp!
      # strip off the starting typedef and ending ;
      raise line unless line =~ /^.*typedef\s*(.+)\s*;\s*$/
      parts = $1.split(/\s+/)

      *def_type, final_type = parts
      def_type = def_type.join(' ')

      # GCC does mapping with __attribute__
      if line =~ /__attribute__/
        if parts.last =~ /__[QHSD]I__|__word__/
          # final_type is the part before __attribute__
          i = parts.index { |part| part =~ /__attribute__/ }
          final_type = parts[i-1]
        else
          final_type = parts.pop
        end

        def_type = case line
                   when /__QI__/   then 'char'
                   when /__HI__/   then 'short'
                   when /__SI__/   then 'int'
                   when /__DI__/   then 'long long'
                   when /__word__/ then 'long'
                   else                 'int'
                   end

        def_type = "unsigned #{def_type}" if line =~ /unsigned/
      end

      if final_type.start_with?('*')
        final_type = final_type[1..-1]
        def_type = "#{def_type} *"
      end

      if resolved = TYPE_MAP[def_type]
        # p final_type => resolved
        TYPE_MAP[final_type] = resolved
        @typedefs[final_type] = resolved
      elsif def_type.end_with?('*')
        TYPE_MAP[final_type] = :pointer
        @typedefs[final_type] = :pointer
      else
        puts "Ignoring #{line}"
      end
    }
  end

  def save
    @typedefs.each_pair { |type, resolved|
      unless type.start_with? '_'
        register "typedef.#{type}", resolved
      end
    }

    # The typedef for pthread_t on Darwin is a multiline definition,
    # which this script does not handle yet:
    # typedef struct _opaque_pthread_t
    # *__darwin_pthread_t;
    if RUBY_PLATFORM =~ /x86_64-darwin/
      register "typedef.pthread_t", :pointer
    end
  end
end

def struct(name)
  struct = StructGenerator.new(name)
  yield struct
  struct.generate
end

def constants(name)
  constants = ConstantsGenerator.new(name)
  yield constants
  constants.generate
end

# Structs used by rubysl-socket

struct 'addrinfo' do |s|
  s.include 'sys/socket.h'
  s.include 'netdb.h'
  s.field :ai_flags, :int
  s.field :ai_family, :int
  s.field :ai_socktype, :int
  s.field :ai_protocol, :int
  s.field :ai_addrlen, :int
  s.field :ai_addr, :pointer
  s.field :ai_canonname, :string
  s.field :ai_next, :pointer
end

struct 'ifaddrs' do |s|
  s.include 'sys/types.h'
  s.include 'ifaddrs.h'
  s.field :ifa_next, :pointer
  s.field :ifa_name, :string
  s.field :ifa_flags, :int
  s.field :ifa_addr, :pointer
  s.field :ifa_netmask, :pointer
  s.field :ifa_broadaddr, :pointer
  s.field :ifa_dstaddr, :pointer
end

struct 'sockaddr' do |s|
  s.include 'sys/socket.h'
  s.field :sa_data, :char_array
  s.field :sa_family, :sa_family_t
end

struct 'sockaddr_in' do |s|
  s.include 'netinet/in.h'
  s.include 'sys/socket.h'
  s.include 'fcntl.h'
  s.include 'sys/stat.h'
  s.field :sin_family, :sa_family_t
  s.field :sin_port, :ushort
  s.field :sin_addr
  s.field :sin_zero, :char_array
end

struct 'sockaddr_in6' do |s|
  s.include 'netinet/in.h'
  s.include 'sys/socket.h'
  s.include 'fcntl.h'
  s.include 'sys/stat.h'
  s.field :sin6_family, :sa_family_t
  s.field :sin6_port, :ushort
  s.field :sin6_flowinfo
  s.field :sin6_addr, :char_array
  s.field :sin6_scope_id
end

struct 'sockaddr_un' do |s|
  s.include 'sys/un.h'
  s.field :sun_family, :sa_family_t
  s.field :sun_path, :char_array
end

struct 'hostent' do |s|
  s.include 'netdb.h'
  s.field :h_name, :string
  s.field :h_aliases, :pointer
  s.field :h_addrtype, :int
  s.field :h_length, :int
  s.field :h_addr_list, :pointer
end

struct 'linger' do |s|
  s.include 'sys/socket.h'
  s.field :l_onoff, :int
  s.field :l_linger, :int
end

struct 'iovec' do |s|
  s.include 'sys/socket.h'
  s.field :iov_base, :pointer
  s.field :iov_len, :size_t
end

struct 'msghdr' do |s|
  s.include 'sys/socket.h'
  s.field :msg_name, :pointer
  s.field :msg_namelen, :int
  s.field :msg_iov, :pointer
  s.field :msg_iovlen, :size_t
  s.field :msg_control, :pointer
  s.field :msg_controllen, :size_t
  s.field :msg_flags, :int
end

struct 'servent' do |s|
  s.include 'netdb.h'
  s.field :s_name, :pointer
  s.field :s_aliases, :pointer
  s.field :s_port, :int
  s.field :s_proto, :pointer
end

# Constants

constants 'errno' do |cg|
  cg.include 'errno.h'
  cg.consts %w[
    EPERM ENOENT ESRCH EINTR EIO ENXIO E2BIG ENOEXEC EBADF ECHILD EDEADLK ENOMEM
    EACCES EFAULT ENOTBLK EBUSY EEXIST EXDEV ENODEV ENOTDIR EISDIR EINVAL ENFILE
    EMFILE ENOTTY ETXTBSY EFBIG ENOSPC ESPIPE EROFS EMLINK EPIPE EDOM ERANGE
    EWOULDBLOCK EAGAIN EINPROGRESS EALREADY ENOTSOCK EDESTADDRREQ EMSGSIZE
    EPROTOTYPE ENOPROTOOPT EPROTONOSUPPORT ESOCKTNOSUPPORT EOPNOTSUPP
    EPFNOSUPPORT EAFNOSUPPORT EADDRINUSE EADDRNOTAVAIL ENETDOWN ENETUNREACH
    ENETRESET ECONNABORTED ECONNRESET ENOBUFS EISCONN ENOTCONN ESHUTDOWN
    ETOOMANYREFS ETIMEDOUT ECONNREFUSED ELOOP ENAMETOOLONG EHOSTDOWN
    EHOSTUNREACH ENOTEMPTY EUSERS EDQUOT ESTALE EREMOTE ENOLCK ENOSYS EOVERFLOW
    EIDRM ENOMSG EILSEQ EBADMSG EMULTIHOP ENODATA ENOLINK ENOSR ENOSTR EPROTO
    ETIME
  ]
end

constants 'file' do |cg|
  cg.include 'stdio.h'
  cg.include 'fcntl.h'
  cg.include 'fnmatch.h'
  cg.include 'sys/stat.h'
  cg.consts %w[
    FNM_CASEFOLD FNM_DOTMATCH FNM_EXTGLOB FNM_NOESCAPE FNM_PATHNAME FNM_SYSCASE

    LOCK_SH LOCK_EX LOCK_NB LOCK_UN

    O_RDONLY O_WRONLY O_RDWR O_ACCMODE O_CREAT O_EXCL O_NOCTTY O_TRUNC O_APPEND
    O_NONBLOCK O_NDELAY O_SYNC O_TMPFILE

    S_IRUSR S_IWUSR S_IXUSR S_IRGRP S_IWGRP S_IXGRP S_IROTH S_IWOTH S_IXOTH
    S_IFMT S_IFIFO S_IFCHR S_IFDIR S_IFBLK S_IFREG S_IFLNK S_IFSOCK S_IFWHT
    S_ISUID S_ISGID S_ISVTX
  ]
end

constants 'io' do |cg|
  cg.include 'stdio.h'
  cg.consts %w[SEEK_SET SEEK_CUR SEEK_END]
end

constants 'fcntl' do |cg|
  cg.include 'unistd.h'
  cg.include 'fcntl.h'
  cg.consts %w[
    F_GETFL F_SETFL
    F_DUPFD F_GETFD F_SETFD FD_CLOEXEC
    F_GETOWN F_SETOWN
    F_GETLK F_SETLK F_SETLKW
    F_RDLCK F_UNLCK F_WRLCK
    F_CHKCLEAN  F_PREALLOCATE  F_SETSIZE F_RDADVISE F_RDAHEAD
    F_READBOOTSTRAP F_WRITEBOOTSTRAP F_NOCACHE F_LOG2PHYS F_GETPATH F_FULLFSYNC
    F_PATHPKG_CHECK F_FREEZE_FS F_THAW_FS F_GLOBAL_NOCACHE F_ADDSIG
    F_MARKDEPENDENCY F_ALLOCATECONTIG F_ALLOCATEALL
  ]
end

constants 'socket' do |cg|
  cg.include 'sys/types.h'
  cg.include 'sys/socket.h'
  cg.include 'netdb.h'
  cg.include 'netinet/in_systm.h'
  cg.include 'netinet/tcp.h'
  cg.include 'netinet/udp.h'
  cg.include 'netinet/in.h'
  cg.include 'net/if.h'

  %w[
    APPLETALK AX25 INET INET6 IPX ISDN LOCAL MAX PACKET ROUTE SNA UNIX UNSPEC
  ].each do |protocol|
    cg.consts %W[AF_#{protocol} PF_#{protocol}]
  end
  cg.consts %w[PF_KEY]

  cg.consts %w[
    AI_ADDRCONFIG AI_ALL AI_CANONNAME AI_NUMERICHOST
    AI_NUMERICSERV AI_PASSIVE AI_V4MAPPED

    EAI_ADDRFAMILY EAI_AGAIN EAI_BADFLAGS EAI_FAIL EAI_FAMILY EAI_MEMORY
    EAI_NODATA EAI_NONAME EAI_OVERFLOW EAI_SERVICE EAI_SOCKTYPE EAI_SYSTEM

    IFF_ALLMULTI IFF_AUTOMEDIA IFF_BROADCAST IFF_DEBUG IFF_DYNAMIC
    IFF_LOOPBACK IFF_MASTER IFF_MULTICAST IFF_NOARP IFF_NOTRAILERS
    IFF_POINTOPOINT IFF_PORTSEL IFF_PROMISC IFF_RUNNING IFF_SLAVE IFF_UP

    IF_NAMESIZE

    INADDR_ALLHOSTS_GROUP INADDR_ANY INADDR_BROADCAST INADDR_LOOPBACK
    INADDR_MAX_LOCAL_GROUP INADDR_NONE INADDR_UNSPEC_GROUP

    INET6_ADDRSTRLEN INET_ADDRSTRLEN

    IPPORT_RESERVED IPPORT_USERRESERVED

    IPPROTO_AH IPPROTO_DSTOPTS IPPROTO_EGP IPPROTO_ESP IPPROTO_FRAGMENT
    IPPROTO_HOPOPTS IPPROTO_ICMP IPPROTO_ICMPV6 IPPROTO_IDP IPPROTO_IGMP
    IPPROTO_IP IPPROTO_IPV6 IPPROTO_NONE IPPROTO_PUP IPPROTO_RAW
    IPPROTO_ROUTING IPPROTO_TCP IPPROTO_TP IPPROTO_UDP

    IPV6_CHECKSUM IPV6_DONTFRAG IPV6_DSTOPTS IPV6_HOPLIMIT IPV6_HOPOPTS
    IPV6_JOIN_GROUP IPV6_LEAVE_GROUP IPV6_MULTICAST_HOPS IPV6_MULTICAST_IF
    IPV6_MULTICAST_LOOP IPV6_NEXTHOP IPV6_PATHMTU IPV6_PKTINFO
    IPV6_RECVDSTOPTS IPV6_RECVHOPLIMIT IPV6_RECVHOPOPTS IPV6_RECVPATHMTU
    IPV6_RECVPKTINFO IPV6_RECVRTHDR IPV6_RECVTCLASS IPV6_RTHDR
    IPV6_RTHDRDSTOPTS IPV6_RTHDR_TYPE_0 IPV6_TCLASS IPV6_UNICAST_HOPS
    IPV6_V6ONLY

    IP_ADD_MEMBERSHIP IP_ADD_SOURCE_MEMBERSHIP IP_BLOCK_SOURCE
    IP_DEFAULT_MULTICAST_LOOP IP_DEFAULT_MULTICAST_TTL IP_DROP_MEMBERSHIP
    IP_DROP_SOURCE_MEMBERSHIP IP_FREEBIND IP_HDRINCL IP_IPSEC_POLICY
    IP_MAX_MEMBERSHIPS IP_MINTTL IP_MSFILTER IP_MTU IP_MTU_DISCOVER
    IP_MULTICAST_IF IP_MULTICAST_LOOP IP_MULTICAST_TTL IP_OPTIONS IP_PASSSEC
    IP_PKTINFO IP_PKTOPTIONS IP_PMTUDISC_DO IP_PMTUDISC_DONT IP_PMTUDISC_WANT
    IP_RECVERR IP_RECVOPTS IP_RECVRETOPTS IP_RECVTOS IP_RECVTTL IP_RETOPTS
    IP_ROUTER_ALERT IP_TOS IP_TRANSPARENT IP_TTL IP_UNBLOCK_SOURCE
    IP_XFRM_POLICY

    MCAST_BLOCK_SOURCE MCAST_EXCLUDE MCAST_INCLUDE MCAST_JOIN_GROUP
    MCAST_JOIN_SOURCE_GROUP MCAST_LEAVE_GROUP MCAST_LEAVE_SOURCE_GROUP
    MCAST_MSFILTER MCAST_UNBLOCK_SOURCE

    MSG_CONFIRM MSG_CTRUNC MSG_DONTROUTE MSG_DONTWAIT MSG_EOR MSG_ERRQUEUE
    MSG_FASTOPEN MSG_FIN MSG_MORE MSG_NOSIGNAL MSG_OOB MSG_PEEK MSG_PROXY
    MSG_RST MSG_SYN MSG_TRUNC MSG_WAITALL

    NI_DGRAM NI_MAXHOST NI_MAXSERV NI_NAMEREQD NI_NOFQDN NI_NUMERICHOST
    NI_NUMERICSERV

    SCM_CREDENTIALS SCM_RIGHTS SCM_TIMESTAMP SCM_TIMESTAMPING SCM_TIMESTAMPNS
    SCM_WIFI_STATUS

    SEEK_CUR SEEK_DATA SEEK_END SEEK_HOLE SEEK_SET

    SHUT_RD SHUT_RDWR SHUT_WR

    SOCK_DGRAM SOCK_PACKET SOCK_RAW SOCK_RDM SOCK_SEQPACKET SOCK_STREAM

    SOL_IP SOL_SOCKET SOL_TCP SOL_UDP

    SO_ACCEPTCONN SO_ATTACH_FILTER SO_BINDTODEVICE SO_BPF_EXTENSIONS
    SO_BROADCAST SO_BUSY_POLL SO_DEBUG SO_DETACH_FILTER SO_DOMAIN SO_DONTROUTE
    SO_ERROR SO_GET_FILTER SO_KEEPALIVE SO_LINGER SO_LOCK_FILTER SO_MARK
    SO_MAX_PACING_RATE SO_NOFCS SO_NO_CHECK SO_OOBINLINE SO_PASSCRED
    SO_PASSSEC SO_PEEK_OFF SO_PEERCRED SO_PEERNAME SO_PEERSEC SO_PRIORITY
    SO_PROTOCOL SO_RCVBUF SO_RCVBUFFORCE SO_RCVLOWAT SO_RCVTIMEO SO_REUSEADDR
    SO_REUSEPORT SO_RXQ_OVFL SO_SECURITY_AUTHENTICATION
    SO_SECURITY_ENCRYPTION_NETWORK SO_SECURITY_ENCRYPTION_TRANSPORT
    SO_SELECT_ERR_QUEUE SO_SNDBUF SO_SNDBUFFORCE SO_SNDLOWAT SO_SNDTIMEO
    SO_TIMESTAMP SO_TIMESTAMPING SO_TIMESTAMPNS SO_TYPE SO_WIFI_STATUS

    TCP_CONGESTION TCP_COOKIE_TRANSACTIONS TCP_CORK TCP_DEFER_ACCEPT
    TCP_FASTOPEN TCP_INFO TCP_KEEPCNT TCP_KEEPIDLE TCP_KEEPINTVL TCP_LINGER2
    TCP_MAXSEG TCP_MD5SIG TCP_NODELAY TCP_QUEUE_SEQ TCP_QUICKACK TCP_REPAIR
    TCP_REPAIR_OPTIONS TCP_REPAIR_QUEUE TCP_SYNCNT TCP_THIN_DUPACK
    TCP_THIN_LINEAR_TIMEOUTS TCP_TIMESTAMP TCP_USER_TIMEOUT TCP_WINDOW_CLAMP

    UDP_CORK
    SOMAXCONN
  ]
end

constants 'process' do |cg|
  cg.include 'sys/wait.h'
  cg.include 'sys/resource.h'
  cg.include 'stdlib.h'

  cg.consts %w[
    EXIT_SUCCESS EXIT_FAILURE
    WNOHANG WUNTRACED
    PRIO_PROCESS PRIO_PGRP PRIO_USER
    RLIMIT_CPU RLIMIT_FSIZE RLIMIT_DATA RLIMIT_STACK RLIMIT_CORE RLIMIT_RSS
    RLIMIT_NPROC RLIMIT_NOFILE RLIMIT_MEMLOCK RLIMIT_AS RLIMIT_SBSIZE
    RLIMIT_RTPRIO RLIMIT_RTTIME RLIMIT_SIGPENDING RLIMIT_MSGQUEUE RLIMIT_NICE
  ]

  cg.consts %w[
    RLIM_INFINITY RLIM_SAVED_MAX RLIM_SAVED_CUR
  ], '%llu', '(unsigned long long)'
end

# The constants come from MRI's signal.c.
constants 'signal' do |cg|
  cg.include 'signal.h'
  cg.include 'sys/signal.h'
  cg.consts %w[
    SIGHUP SIGINT SIGQUIT SIGILL SIGTRAP SIGIOT SIGABRT SIGEMT SIGFPE SIGKILL
    SIGBUS SIGSEGV SIGSYS SIGPIPE SIGALRM SIGTERM SIGURG SIGSTOP SIGTSTP
    SIGCONT SIGCHLD SIGCLD SIGCHLD SIGTTIN SIGTTOU SIGIO SIGXCPU SIGXFSZ
    SIGVTALRM SIGPROF SIGWINCH SIGUSR1 SIGUSR2 SIGLOST SIGMSG SIGPWR SIGPOLL
    SIGDANGER SIGMIGRATE SIGPRE SIGGRANT SIGRETRACT SIGSOUND SIGINFO
  ]
end

constants 'dlopen' do |cg|
  cg.include 'dlfcn.h'
  cg.consts %w[RTLD_LAZY RTLD_NOW RTLD_LOCAL RTLD_GLOBAL]
end

TypesGenerator.new.generate
