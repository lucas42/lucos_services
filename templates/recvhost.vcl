	} elsif (req.http.host == "$domain$") {
		set req.backend = lucos_$id$;
		$custom$
