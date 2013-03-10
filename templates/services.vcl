
$backends$

sub vcl_recv {
	if (false) {
		# Nothing goes here - it's just easier for templating
$recvhosts$
	}
	if (req.url ~ "/resources") {
			return(pass);
	}
}